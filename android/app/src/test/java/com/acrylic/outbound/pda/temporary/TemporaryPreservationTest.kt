package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.evidence.LocalDraftStopRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryPreservationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun acceptedInputPrecedesStopAndNewIntakeIsImmediatelyFenced() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val engine = TemporaryEngine(f.newStore()) {}
            val release = CountDownLatch(1); val locked = CountDownLatch(1)
            try {
                engine.resume(f.id); withTimeout(10000) { engine.state.first { it.ready && it.queued == 0 } }
                val holder = async(Dispatchers.IO) { f.db.runInTransaction { locked.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }
                assertTrue(locked.await(10, TimeUnit.SECONDS)); assertTrue(engine.receive(f.barcodes[0], "MANUAL"))
                val stopped = engine.preserve(f.id)
                assertFalse(stopped.isCompleted); assertFalse(engine.receive(f.barcodes[1], "BROADCAST"))
                release.countDown(); holder.await(); val original = withTimeout(10000) { stopped.await() }
                withTimeout(10000) { engine.state.first { it.queued == 0 } }
                val capture = f.db.temporaryScans().captures(f.id).single()
                assertEquals(f.barcodes[0], capture.rawInput)
                val events = f.db.temporaryScans().events(f.id)
                assertEquals("LOCAL_EVIDENCE_STOP", events.last().type); assertTrue(events.any { it.type == "RECEIVED" })
                assertEquals(original, LocalDraftStopRepository(f.store).read(f.id)); assertEquals(1, f.fixture.stopSigner.calls)
            } finally { release.countDown(); engine.close() }
        }
    }
    @Test fun failedAcceptedInputPreventsAnyStopSignature() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val engine = TemporaryEngine(f.newStore()) {}
            try {
                engine.resume(f.id); withTimeout(10000) { engine.state.first { it.ready && it.queued == 0 } }
                f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_received_test BEFORE INSERT ON temporary_capture
                    BEGIN SELECT RAISE(ABORT,'injected storage failure'); END""")
                assertTrue(engine.receive(f.barcodes[0], "BROADCAST")); val result = engine.preserve(f.id)
                val failure = runCatching { withTimeout(10000) { result.await() } }.exceptionOrNull()
                assertNotNull(failure); assertTrue(failure!!.message.orEmpty().contains("尚未耐久保存"))
                withTimeout(10000) { engine.state.first { it.queued == 0 } }
                assertNull(f.db.localDraftStops().get(f.id)); assertNull(f.db.localDraftStopSignatures().get(f.id))
                assertEquals("SCANNING", f.db.temporaryDrafts().get(f.id)!!.state); assertEquals(0, f.fixture.stopSigner.calls)
                assertFalse(engine.receive(f.barcodes[0], "BROADCAST"))
            } finally { engine.close() }
        }
    }
    @Test fun draftOrAlreadyStoppedSourceCanBePreservedWithoutStartingScanning() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val engine = TemporaryEngine(f.newStore()) {}
            try {
                val first = withTimeout(10000) { engine.preserve(f.id).await() }
                withTimeout(10000) { engine.state.first { it.queued == 0 } }
                f.fixture.stopSigner.unavailable = true
                assertEquals(first, withTimeout(10000) { engine.preserve(f.id).await() }); assertEquals(1, f.fixture.stopSigner.calls)
                withTimeout(10000) { engine.state.first { it.queued == 0 } }
            } finally { engine.close() }
        }
    }
}
