package com.acrylic.outbound.pda.work

import com.acrylic.outbound.pda.device.WorkSound
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class WorkSoundReadinessTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun unloadedSoundPreservesDurableSaveAndDoesNotReplayAfterReopen() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val sound = WorkSound(f.context)
            val feedback = java.util.concurrent.CopyOnWriteArrayList<String>()
            val engine = WorkEngine(f.store) { type ->
                assertFalse("Feedback must run after the save transaction", f.db.inTransaction())
                if (type == "SAVED") assertEquals(1L, f.db.scans().total(f.assignment).count)
                sound.play(type); feedback.add(type)
            }
            try {
                engine.resume(f.assignment)
                await(engine) { it.ready && it.queued == 0 }
                assertTrue(engine.receive(f.barcodes[0], "BROADCAST"))
                val saved = await(engine) { it.order?.actualCount == 1L && it.queued == 0 }
                assertTrue(saved.ready); assertTrue(saved.message.contains("已保存到本机"))
                assertEquals(listOf("SAVED"), feedback.toList())
                assertEquals("声音未就绪，请以页面保存状态为准", sound.error)
                val original = saved.recent.single()
                engine.close(); f.reload(); feedback.clear(); f.processor.drain(f.assignment)
                assertEquals(original, f.db.scans().page(f.assignment, 0, 10).single())
                assertEquals(1, f.db.captures().page(f.assignment, 0, 10).size)
                assertTrue(f.sounds.isEmpty()); assertTrue(feedback.isEmpty())
            } finally { engine.close() }
        }
    }

    @Test fun failedCommitNeverEmitsSavedEvenWhenSoundIsUnloaded() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val sound = WorkSound(f.context)
            val calls = mutableListOf<String>()
            val processor = WorkProcessor(f.store) { type, _ -> sound.play(type); calls.add(type) }
            val capture = f.receive()
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER sound_tail BEFORE UPDATE OF state ON work_capture
                WHEN NEW.state='COMMITTED' BEGIN SELECT RAISE(ABORT,'sound tail rollback'); END""")
            assertThrows(Exception::class.java) { processor.drain(f.assignment) }
            assertTrue(calls.isEmpty()); assertEquals("", sound.error)
            assertEquals("CONFIRM_REQUESTED", f.db.captures().get(capture.id)!!.state)
            assertNull(f.db.scans().get(capture.id)); assertNull(f.db.scans().guard(f.barcodes[0]))
            assertEquals(0L, f.db.scans().total(f.assignment).count)
            assertFalse(f.db.work().events(f.assignment).any { it.type == "SAVED" })
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER sound_tail")
            processor.drain(f.assignment)
            assertEquals(listOf("SAVED"), calls); assertEquals("COMMITTED", f.db.captures().get(capture.id)!!.state)
            assertEquals("声音未就绪，请以页面保存状态为准", sound.error)
            f.reload(); f.processor.drain(f.assignment)
            assertEquals(1L, f.db.scans().total(f.assignment).count); assertTrue(f.sounds.isEmpty())
        }
    }

    private suspend fun await(engine: WorkEngine, predicate: (WorkUi) -> Boolean): WorkUi =
        withTimeout(15000) { engine.state.first(predicate) }
}
