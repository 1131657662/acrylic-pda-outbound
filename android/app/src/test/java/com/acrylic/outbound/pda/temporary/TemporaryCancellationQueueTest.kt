package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryCancellationQueueTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun acceptedInputAheadOfCancellationCannotDisappear() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val engine = TemporaryEngine(f.newStore()) {}
            try {
                engine.resume(f.id); withTimeout(10000) { engine.state.first { it.ready && it.queued == 0 } }
                assertTrue(engine.receive(f.barcodes[0], "BROADCAST"))
                val request = TemporaryCancelInput(checkNotNull(f.db.temporaryDrafts().get(f.id)).revision, "取消车辆")
                assertTrue(runCatching { engine.cancelDraft(f.id, request).await() }.isFailure)
                withTimeout(10000) { engine.state.first { it.queued == 0 } }
                val original = f.db.temporaryScans().captures(f.id).single()
                assertEquals(f.barcodes[0], original.barcode)
                assertTrue(original.state in setOf("RECEIVED", "CONFIRM_REQUESTED", "COMMITTED"))
                assertEquals("SCANNING", f.db.temporaryDrafts().get(f.id)?.state)
                assertFalse(engine.receive(f.barcodes[1], "BROADCAST"))
                assertTrue(f.db.temporaryScans().events(f.id).none { it.type == "TEMP_DRAFT_CANCELLED" })
            } finally { engine.close() }
        }
    }
    @Test fun applicationQueueCancelsZeroWorkThenRejectsNewIntake() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val engine = TemporaryEngine(f.newStore()) {}
            try {
                engine.resume(f.id); withTimeout(10000) { engine.state.first { it.ready && it.queued == 0 } }
                val request = TemporaryCancelInput(checkNotNull(f.db.temporaryDrafts().get(f.id)).revision, "取消车辆")
                assertEquals("CANCELLED", engine.cancelDraft(f.id, request).await().state)
                assertFalse(engine.receive(f.barcodes[0], "BROADCAST"))
                engine.resume(f.id); withTimeout(10000) { engine.state.first { !it.ready && it.queued == 0 } }
                assertEquals(0L, f.db.temporaryScans().total(f.id).count)
                assertTrue(f.db.temporaryScans().captures(f.id).isEmpty())
            } finally { engine.close() }
        }
    }
}
