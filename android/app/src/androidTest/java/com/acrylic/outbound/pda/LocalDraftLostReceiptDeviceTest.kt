package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

/** Two processes, actual SQLite, synthetic durable peer. No real HTTP/server/Keystore claim. */
class LocalDraftLostReceiptDeviceTest {
    @Before fun guard() = R06A11DeviceGuard.require("PdaDownloadVerify")

    @Test fun phase1() {
        val roots = JSONArray()
        for (operation in listOf("OPEN", "ITEM", "VERIFY")) {
            R06A11DeviceWork.create("lost-$operation").use { work ->
                work.seedStoppedWork()
                work.repository().prepare(work.id)
                val before = R06A11DeviceSnapshot.capture(work).toString()
                val peer = R06A11DurablePeer(work)
                peer.prepare(R06A11ReceiptIssuer())
                peer.drop = operation
                assertThrows(IOException::class.java) { work.repository().resume(work.id) }
                val dropped = work.requests().first { it.operation == operation }
                assertEquals("RESULT_UNKNOWN", work.db.localDraftTransfers().receipt(dropped.originalKey)!!.state)
                assertEquals(1, peer.calls("POST").count { it == dropped.originalKey })
                work.facts.put("droppedKey", dropped.originalKey).put("operation", operation)
                work.persist()
                File(work.root, "before.json").writeText(before)
                assertEquals(before, R06A11DeviceSnapshot.capture(work).toString())
                roots.put(work.root.absolutePath)
                println("R06A11_LOSS phase=1 operation=$operation key=${dropped.originalKey} RESULT_UNKNOWN")
            }
        }
        val manifest = JSONObject().put("run", runId()).put("pid", android.os.Process.myPid()).put("roots", roots)
        marker().writeText(manifest.toString())
    }

    @Test fun phase2() {
        val manifest = JSONObject(marker().readText())
        assertEquals(runId(), manifest.getString("run"))
        assertNotEquals("Must be a fresh process", manifest.getInt("pid"), android.os.Process.myPid())
        val roots = manifest.getJSONArray("roots")
        assertEquals(3, roots.length())
        for (index in 0 until roots.length()) {
            R06A11DeviceWork.reopen(roots.getString(index)).use { verifyRecovery(it) }
        }
    }

    private fun verifyRecovery(work: R06A11DeviceWork) {
        val before = File(work.root, "before.json").readText()
        assertEquals(before, R06A11DeviceSnapshot.capture(work).toString())
        val peer = R06A11DurablePeer(work)
        val key = work.facts.getString("droppedKey")
        val postsBefore = peer.calls("POST").size
        val plan = work.repository().prepare(work.id)
        assertEquals("ORIGINALS_STORED", work.repository().resume(work.id).state)
        assertEquals(before, R06A11DeviceSnapshot.capture(work).toString())
        assertEquals(plan.requestCount, peer.calls("POST").size.toLong())
        assertEquals(plan.requestCount, peer.calls("POST").toSet().size.toLong())
        assertEquals(1, peer.calls("POST").count { it == key })
        assertTrue(peer.calls("GET").count { it == key } >= 2)
        assertEquals(0, work.signer.calls)
        val calls = peer.calls("GET").size + peer.calls("POST").size
        assertEquals("ORIGINALS_STORED", work.repository().resume(work.id).state)
        assertEquals(calls, peer.calls("GET").size + peer.calls("POST").size)
        work.requests().forEach { assertEquals("VERIFIED", work.db.localDraftTransfers().receipt(it.originalKey)!!.state) }
        assertFalse(work.db.localDraftQueue().get(work.id)!!.autoRetry)
        println("R06A11_LOSS phase=2 operation=${work.facts.getString("operation")} " +
            "requests=${plan.requestCount} postsBefore=$postsBefore droppedKeyPosts=1 stopSigns=0 ORIGINALS_STORED")
    }

    private fun runId(): String = checkNotNull(InstrumentationRegistry.getArguments().getString("r06a11Run"))
        .also { check(it.matches(Regex("[A-Za-z0-9-]{1,80}"))) }

    private fun marker(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "r06a11-loss-${runId()}.json")
    }
}
