package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.DeviceAuditTrail
import com.acrylic.outbound.pda.retention.AuditArchiveVerifier
import com.acrylic.outbound.pda.retention.AuditProofFactory
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftStopVectorExportTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun emptyDraftOriginalSet() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f -> export(f, f.id, "empty-draft") }
    }
    @Test fun pendingManualOriginalSet() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.receive(source = "MANUAL"); export(f, f.id, "pending-manual")
        }
    }
    @Test fun committedAndVoidOriginalSet() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val saved = f.scan(); f.processor.corrections.voidLast(f.id, saved.id, "合成卸车")
            f.receive(source = "MANUAL"); export(f, f.id, "void-and-pending")
        }
    }
    @Test fun committedActiveOriginalSet() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f -> f.start(); f.scan(); export(f, f.id, "committed-active") }
    }
    @Test fun otherSourceCaptureDoesNotBecomeOwnCapture() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); val plan = f.plan().copy(revision = 0)
            val next = java.util.UUID.randomUUID().toString(); f.drafts.create(next)
            val saved = f.drafts.save(next, plan); f.drafts.start(next, saved.revision)
            export(f, next, "other-source")
        }
    }
    @Test fun originalArchiveIncludedWithFullAuditPrefix() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val source = f.catalog.request
            val events = DeviceAuditTrail(f.db).page(source.deviceId, 0)
            val payload = AuditArchiveVerifier.payload(source.deviceId, 0, events.size.toLong(), JSONArray(events))
            val factory = AuditProofFactory(); val proof = factory.proof(payload, source.warehouseId, source.recoveryEpoch)
            f.db.auditArchives().insert(factory.row(proof).copy(proofJson = SubmissionJson.canonical(proof),
                signingKeyJson = SubmissionJson.canonical(factory.key)))
            export(f, f.id, "archive-prefix")
        }
    }

    private fun export(f: TemporaryWorkFixture, id: String, name: String) {
        val stop = LocalDraftStopRepository(f.store).stop(id)
        val proof = checkNotNull(f.db.localDraftStopSignatures().get(id))
        val items = JSONArray(); var after = 0L
        while (true) {
            val page = f.db.localDraftStops().page(id, after); if (page.isEmpty()) break
            for (row in page) items.put(JSONObject().put("ordinal", row.ordinal).put("kind", row.kind)
                .put("recordId", row.recordId).put("deviceSeq", row.deviceSeq).put("recordStatus", row.recordStatus)
                .put("bytesSha256", row.bytesSha256).put("raw", JSONObject(row.rawBytes.toString(Charsets.UTF_8))))
            after = page.last().ordinal
        }
        assertEquals(stop.recordCount, items.length().toLong())
        assertEquals(stop, LocalDraftStopRepository(f.store).read(id))
        val vector = JSONObject().put("metadata", JSONObject(stop.metadataJson)).put("proof", JSONObject(proof.proofJson))
            .put("publicKey", proof.publicKey).put("items", items)
        System.getenv("LOCAL_DRAFT_SNAPSHOT_VECTORS")?.let { dir ->
            val target = File(dir); check(target.isDirectory)
            File(target, "$name.json").writeText(SubmissionJson.canonical(vector), Charsets.UTF_8)
        }
    }
}
