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

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryHandoverScanningTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun independentHandoverGateScansAndFreezesCumulativeOriginalPrefix() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            val store = opened(f); val original = JSONObject(f.stage.repository.read(f.id).payloadBytes.toString(Charsets.UTF_8))
            assertEquals(1L, store.total(f.client).count)
            val originalScan = original.getJSONArray("scans").getJSONObject(0)
            val bale = store.source(f.client).bales.values.first {
                it.barcode != originalScan.getString("barcode") && it.status == "AVAILABLE" }
            val processor = TemporaryProcessor(store) {}
            val capture = processor.reception.receive(TemporaryInput(f.client, bale.barcode, "BROADCAST", store.sample()))
            processor.drain(f.client)
            assertEquals("COMMITTED", f.db.temporaryScans().capture(capture.id)!!.state)
            assertEquals(2L, capture.orderScanSeq); assertEquals(2L, store.total(f.client).count)
            assertEquals(listOf(1L, 2L), store.page(f.client, 0).map { it.orderScanSeq })
            val frozen = TemporaryFreeze(store).freeze(f.client, "合成短装，等待专项批准")
            val body = JSONObject(frozen.payload)
            for (field in listOf("captures", "scans", "events")) for (i in 0 until original.getJSONArray(field).length())
                assertEquals(SubmissionJson.canonical(original.getJSONArray(field).getJSONObject(i)), SubmissionJson.canonical(body.getJSONArray(field).getJSONObject(i)))
            assertEquals("2", body.getString("expectedCount")); assertEquals("2", body.getString("captureHighWatermark"))
            assertEquals(f.ticket, body.getJSONObject("temporaryHandover").getString("ticketId"))
            assertFalse(body.has("temporaryContinuation")); assertTrue(body.isNull("assignmentId"))
            assertNotNull(f.db.localE2aStops().get(f.client))
        }
    }

    @Test fun retainedBarcodeIsNotEditableAndClosedHandoverGateRejectsNewInput() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            val store = opened(f); val retained = f.db.temporaryHandoverGuards().forCase(f.id).single()
            val processor = TemporaryProcessor(store) {}
            val capture = processor.reception.receive(TemporaryInput(f.client, retained.barcode, "BROADCAST", store.sample()))
            processor.drain(f.client)
            assertEquals("INVALID", f.db.temporaryScans().capture(capture.id)!!.state)
            assertNull(f.db.temporaryScans().scan(capture.id)); assertEquals(1L, store.total(f.client).count)
            val closed = TemporaryWorkStore(f.db, f.stage.connection, true, false, store.sample); closed.load(f.client)
            assertFalse(closed.canScan(f.db.temporaryDrafts().get(f.client)!!))
            assertThrows(Exception::class.java) { TemporaryHandoverWork(closed, false).start(f.client) }
        }
    }

    private fun opened(f: TemporaryHandoverActivationFixture): TemporaryWorkStore {
        f.repository.install(f.id, f.original); f.acknowledge()
        val store = f.store; TemporaryHandoverWork(store, true).start(f.client); return store
    }
}
