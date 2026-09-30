package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.evidence.LocalDraftReceipt
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Actual Room and product verifiers, synthetic authority/session/key and observed transport calls. */
class LocalDraftIntegrityDeviceTest {
    @Before fun guard() = R06A11DeviceGuard.require("PdaDownloadVerify")

    @Test fun badOriginalBlocksReadAndPost() = damaged("original",
        "UPDATE local_draft_stop_item SET rawBytes=X'7b7d' WHERE clientOrderId=? AND ordinal=1")

    @Test fun badRequestBlocksReadAndPost() = damaged("request",
        "UPDATE local_draft_request SET requestJson='{}' WHERE clientOrderId=? AND position=0")

    private fun damaged(label: String, sql: String) {
        prepared(label) { work, peer ->
            work.db.openHelper.writableDatabase.execSQL(sql, arrayOf<Any>(work.id))
            assertThrows(Exception::class.java) { work.repository().prepare(work.id) }
            assertThrows(Exception::class.java) { work.repository().resume(work.id) }
            assertFalse(checkNotNull(work.db.localDraftQueue().get(work.id)).autoRetry)
            assertEquals("EVIDENCE_STOPPED", work.db.temporaryDrafts().get(work.id)!!.state)
            assertTrue(peer.calls("GET").isEmpty())
            assertTrue(peer.calls("POST").isEmpty())
            assertEquals(1, work.signer.calls)
            println("R06A11_INTEGRITY $label blocked GET=0 POST=0 autoRetry=false")
        }
    }

    @Test fun signedUnsafeReceiptIsPreservedAndCannotRestart() {
        prepared("receipt") { work, peer ->
            val issuer = R06A11ReceiptIssuer().apply { change = { it.put("localDeletionAllowed", true) } }
            peer.prepare(issuer)
            assertThrows(IllegalStateException::class.java) { work.repository().resume(work.id) }
            val key = work.requests().first().originalKey
            val saved = checkNotNull(work.db.localDraftTransfers().receipt(key))
            assertEquals("RECEIVED", saved.state)
            work.persist()
            work.db.close()
            R06A11DeviceWork.reopen(work.root.absolutePath).use { recovered ->
                val durablePeer = R06A11DurablePeer(recovered)
                assertThrows(IllegalStateException::class.java) { recovered.repository().prepare(recovered.id) }
                assertFalse(recovered.db.localDraftQueue().get(recovered.id)!!.autoRetry)
                assertArrayEquals(saved.responseBytes, recovered.db.localDraftTransfers().receipt(key)!!.responseBytes)
                assertEquals(1, durablePeer.calls("POST").size)
                assertEquals(0, recovered.signer.calls)
            }
            println("R06A11_INTEGRITY unsafe signed receipt retained POST=1 no retry or re-sign")
        }
    }

    @Test fun laterBadReceiptBlocksEarlierUnknownBeforeNetwork() {
        prepared("later-receipt") { work, peer ->
            val last = work.requests().last()
            work.db.localDraftTransfers().insertReceipt(LocalDraftReceipt(last.originalKey, "RECEIVED", "{}".toByteArray()))
            assertThrows(Exception::class.java) { work.repository().resume(work.id) }
            assertTrue(peer.calls("GET").isEmpty())
            assertTrue(peer.calls("POST").isEmpty())
            assertFalse(work.db.localDraftQueue().get(work.id)!!.autoRetry)
            println("R06A11_INTEGRITY later receipt blocks earlier unknown GET=0 POST=0")
        }
    }

    private fun prepared(label: String, action: (R06A11DeviceWork, R06A11DurablePeer) -> Unit) {
        R06A11DeviceWork.create(label).use { work ->
            work.seedStoppedWork()
            val plan = work.repository().prepare(work.id)
            assertEquals(work.stop().recordCount + 2, plan.requestCount)
            assertEquals("READY", work.repository().checked(work.id).state)
            val peer = R06A11DurablePeer(work)
            peer.prepare(R06A11ReceiptIssuer())
            action(work, peer)
        }
    }
}
