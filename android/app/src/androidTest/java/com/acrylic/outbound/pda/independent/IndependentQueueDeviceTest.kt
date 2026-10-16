package com.acrylic.outbound.pda.independent

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Separate copies for injected failure, original-ID recovery and bounded admission. */
class IndependentQueueDeviceTest {
    @Test fun nativeQueuePreservesAcceptedInputAcrossTheSelectedBoundary() {
        NativeIndependentQueueFixture().use { f ->
            when (InstrumentationRegistry.getArguments().getString("queuePhase")) {
                "fault" -> tailFailure(f)
                "recover" -> recover(f)
                "overflow" -> overflow(f)
                else -> error("Unknown synthetic queue phase")
            }
        }
    }

    private fun tailFailure(f: NativeIndependentQueueFixture) {
        f.open()
        assertEquals("WAITING_CONFIRM", f.engine.state.value.candidate?.processing?.state)
        f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER native_queue_tail BEFORE INSERT ON independent_task_event
            WHEN NEW.type='INDEPENDENT_SAVED' BEGIN SELECT RAISE(ABORT,'native queue tail'); END""")
        f.engine.confirm(f.capture.id)
        f.await { f.engine.state.value.queued == 0 && f.engine.state.value.blocked.contains("native queue tail") }
        assertEquals("CONFIRM_REQUESTED", f.db.independentScans().capture(f.capture.id)?.processing?.state)
        assertNull(f.db.independentScans().scan(f.capture.id))
        assertNull(f.db.independentScans().guard(f.capture.barcode))
        assertEquals(0L, f.db.independentScans().total(f.task.id).count)
        assertTrue(f.feedback.isEmpty())
        assertFalse(f.engine.receive(f.capture.rawInput, "BROADCAST"))
        assertEquals(listOf(f.capture), f.db.independentScans().captures(f.task.id).map { it.capture })
        f.record("fault")
    }

    private fun recover(f: NativeIndependentQueueFixture) {
        assertEquals("CONFIRM_REQUESTED", f.db.independentScans().capture(f.capture.id)?.processing?.state)
        f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER native_queue_tail")
        f.open(); f.await { f.engine.state.value.ready && f.engine.state.value.queued == 0 }
        assertEquals(listOf("SAVED"), f.feedback.toList())
        assertEquals("COMMITTED", f.db.independentScans().capture(f.capture.id)?.processing?.state)
        assertNotNull(f.db.independentScans().scan(f.capture.id))
        assertNotNull(f.db.independentScans().guard(f.capture.barcode))
        f.engine.pause()
        assertFalse(f.engine.receive(f.capture.rawInput, "BROADCAST"))
        f.restart(); f.await { f.engine.state.value.ready && f.engine.state.value.queued == 0 }
        assertEquals(listOf("SAVED"), f.feedback.toList())
        assertEquals(listOf(f.capture), f.db.independentScans().captures(f.task.id).map { it.capture })
        assertEquals(1L, f.db.independentScans().total(f.task.id).count)
        f.record("recover")
    }

    private fun overflow(f: NativeIndependentQueueFixture) {
        f.open(); f.await { f.engine.state.value.ready && f.engine.state.value.queued == 0 }
        val original = f.db.independentScans().captures(f.task.id)
        f.hold.set(true)
        try {
            assertTrue(f.engine.receive(f.capture.rawInput, "BROADCAST"))
            assertTrue(f.entered.await(10, TimeUnit.SECONDS))
            repeat(64) { assertTrue(f.engine.receive(f.capture.rawInput, "BROADCAST")) }
            assertFalse(f.engine.receive(f.capture.rawInput, "BROADCAST"))
            assertTrue(f.engine.state.value.blocked.contains("入口已满"))
            f.engine.pause(); f.release.countDown()
            f.await { f.engine.state.value.queued == 0 }
            val captures = f.db.independentScans().captures(f.task.id)
            assertEquals(66, captures.size)
            assertEquals(original, captures.take(1))
            assertEquals((2L..66L).toList(), captures.drop(1).map { it.capture.sequence })
            assertTrue(captures.drop(1).all { it.processing.state == "RECEIVED" && it.capture.rawInput == f.capture.rawInput })
            assertEquals(1L, f.db.independentScans().total(f.task.id).count)
            assertTrue(f.feedback.isEmpty())
            f.record("overflow")
        } finally { f.release.countDown() }
    }
}
