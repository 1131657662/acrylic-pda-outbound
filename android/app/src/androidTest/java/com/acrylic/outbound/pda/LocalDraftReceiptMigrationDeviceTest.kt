package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.evidence.LocalDraftQueueGuard
import com.acrylic.outbound.pda.sync.SyncRun
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** Product-created requests and verified receipts across Room migration plus real process death. */
class LocalDraftReceiptMigrationDeviceTest {
    @Before fun guard() = R06A11DeviceGuard.require("PdaDownloadVerify")

    @Test fun prepareDirectory() {
        check(checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)).isDirectory)
        check(!marker().exists()) { "Run label already used; preserve old evidence" }
        println("R06_RECEIPT_MIGRATION setup only")
    }

    @Test fun beforeMigration() {
        check(!marker().exists()) { "Never overwrite a prior run" }
        val roots = JSONArray()
        for (complete in listOf(false, true)) {
            R06A11DeviceWork.create("receipt-migration").use { work ->
                seed(work, complete)
                work.facts.put("complete", complete)
                work.persist()
                DraftMigrationFiles.freeze(work)
                roots.put(work.root.absolutePath)
            }
        }
        marker().writeText(JSONObject().put("run", runId()).put("pid", android.os.Process.myPid())
            .put("roots", roots).toString())
        println("R06_RECEIPT_MIGRATION phase=before cases=2 pid=${android.os.Process.myPid()}")
    }

    private fun seed(work: R06A11DeviceWork, complete: Boolean) {
        work.seedStoppedWork()
        val plan = work.repository().prepare(work.id)
        val requests = work.requests()
        assertEquals(plan, work.repository().prepare(work.id))
        assertEquals(requests, work.requests())
        assertEquals(1, work.signer.calls)
        val peer = R06A11DurablePeer(work)
        peer.prepare(R06A11ReceiptIssuer())
        work.repository().resume(work.id, if (complete) SyncRun() else SyncRun(batches = 1))
        val expectedReceipts = if (complete) plan.requestCount.toInt() else 1
        assertEquals(expectedReceipts, requests.count { work.db.localDraftTransfers().receipt(it.originalKey) != null })
        assertEquals(expectedReceipts, peer.calls("POST").size)
        val queue = checkNotNull(work.db.localDraftQueue().get(work.id))
        work.db.runInTransaction {
            work.db.localDraftQueue().update(queue.copy(autoRetry = false,
                state = if (complete) "ORIGINALS_STORED" else "ACTION_REQUIRED", error = "migration pause"))
        }
        work.facts.put("receipts", expectedReceipts).put("requests", requests.size)
        work.facts.put("peerTrace", File(work.root, "peer/calls.txt").readText())
        println("R06_RECEIPT_MIGRATION seeded complete=$complete requests=${requests.size} receipts=$expectedReceipts")
    }

    @Test fun afterMigration() {
        val manifest = JSONObject(marker().readText())
        assertEquals(runId(), manifest.getString("run"))
        assertNotEquals(manifest.getInt("pid"), android.os.Process.myPid())
        val roots = manifest.getJSONArray("roots")
        assertEquals(2, roots.length())
        for (index in 0 until roots.length()) {
            // reopen only allocates Room; the first SQLite open below performs the migration.
            R06A11DeviceWork.reopen(roots.getString(index)).use { work ->
                DraftMigrationFiles.migrate(work.facts)
                verifyProductReads(work)
                DraftMigrationFiles.verify(work)
                File(work.root, "migration-result.json").writeText(JSONObject()
                    .put("pid", android.os.Process.myPid()).put("requests", work.facts.getInt("requests"))
                    .put("receipts", work.facts.getInt("receipts")).put("result", "PASS").toString())
            }
        }
        println("R06_RECEIPT_MIGRATION phase=after cases=2 pid=${android.os.Process.myPid()} PASS")
    }

    private fun verifyProductReads(work: R06A11DeviceWork) {
        var calls = 0
        work.connection.receive = { calls++; error("Migration must not send") }
        val progress = work.repository().checked(work.id)
        assertEquals(work.facts.getInt("receipts").toLong(), progress.verifiedRequests)
        assertEquals(work.facts.getInt("requests").toLong(), progress.totalRequests)
        assertEquals(if (work.facts.getBoolean("complete")) "ORIGINALS_STORED" else "READY", progress.state)
        LocalDraftQueueGuard(work.db).reconcile(null)
        val queue = checkNotNull(work.db.localDraftQueue().get(work.id))
        assertFalse(queue.autoRetry)
        assertEquals("migration pause", queue.error)
        assertEquals(0, work.signer.calls)
        assertEquals(0, calls)
        assertEquals(work.facts.getString("peerTrace"), File(work.root, "peer/calls.txt").readText())
    }

    private fun runId(): String = checkNotNull(InstrumentationRegistry.getArguments().getString("r06a11Run"))
        .also { check(it.matches(Regex("[A-Za-z0-9-]{1,80}"))) }

    private fun marker(): File = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        "r06-receipt-migration-${runId()}.json")
}
