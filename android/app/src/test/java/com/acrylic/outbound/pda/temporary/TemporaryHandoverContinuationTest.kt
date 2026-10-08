package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.submission.SubmissionJson
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryHandoverContinuationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun handoverThenContinuationRetainsPreviousDeviceFactsAcrossReopen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverContinuationFixture(folder.root).use { f ->
            val original = f.frozen; val body = JSONObject(original.payload)
            val next = TemporaryContinuationActivation(f.store).activate(f.draft.clientOrderId, f.raw())
            f.acknowledge(next)
            f.handover.stage.local.reopen()
            val store = f.store
            TemporaryContinuationWork(store).start(next.clientOrderId)
            scanRemaining(store,next.clientOrderId)
            val frozen = TemporaryFreeze(store).freeze(next.clientOrderId, "第三轮保全")
            val actual = JSONObject(frozen.payload)
            assertEquals("3", actual.getString("workRound"))
            assertEquals("2", actual.getString("expectedCount"))
            assertEquals(original, f.db.temporarySubmissions().get(original.id))
            for (field in listOf("captures", "scans", "events")) {
                val prefix = body.getJSONArray(field); val rows = actual.getJSONArray(field)
                for (i in 0 until prefix.length()) assertEquals(SubmissionJson.canonical(prefix.getJSONObject(i)),
                    SubmissionJson.canonical(rows.getJSONObject(i)))
            }
            assertNotEquals(next.creatorDeviceId, actual.getJSONArray("captures").getJSONObject(0).getString("originDeviceId"))
        }
    }
    private fun scanRemaining(store: TemporaryWorkStore,id: String) {
        val old=store.page(id,0).map { it.barcode }.toSet()
        val bale=store.source(id).bales.values.first { it.status=="AVAILABLE" && it.barcode !in old }
        val processor=TemporaryProcessor(store) {}
        val capture=processor.reception.receive(TemporaryInput(id,bale.barcode,"BROADCAST",store.sample()))
        processor.drain(id)
        assertEquals("COMMITTED",store.db.temporaryScans().capture(capture.id)!!.state)
        assertEquals(2L,capture.orderScanSeq)
    }

    @Test fun signedWrongSourceClientCannotInstallAnotherRound() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverContinuationFixture(folder.root).use { f ->
            val raw = f.raw { it.getJSONObject("originalContext").put("clientOrderId", UUID.randomUUID().toString()) }
            assertThrows(IllegalStateException::class.java) { TemporaryContinuationActivation(f.store).activate(f.draft.clientOrderId, raw) }
            assertNull(f.db.temporaryContinuations().forSource(f.draft.clientOrderId))
            assertEquals(f.frozen, f.db.temporarySubmissions().get(f.frozen.id))
        }
    }

    @Test fun changedHandoverOriginalCannotBecomeContinuationPrefix() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverContinuationFixture(folder.root).use { f ->
            val draft = f.draft; val store = f.store; store.load(draft.clientOrderId)
            val old = TemporaryHandoverBaseline(store).read(draft, false).original
            old.getJSONArray("captures").getJSONObject(0).put("rawInput", "rewritten")
            val input = TemporaryContinuationProtocol.Input(draft, f.frozen, store.source(draft.clientOrderId).manifest,
                f.handover.stage.session, f.handover.stage.signer.key, old)
            assertThrows(IllegalStateException::class.java) { TemporaryContinuationProtocol.verify(f.raw(), input) }
            assertNull(f.db.temporaryContinuations().forSource(draft.clientOrderId))
        }
    }
}
