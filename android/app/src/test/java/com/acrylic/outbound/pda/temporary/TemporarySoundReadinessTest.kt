package com.acrylic.outbound.pda.temporary

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
class TemporarySoundReadinessTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun unloadedSoundKeepsSavedTemporaryInputDurableWithoutReconfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start()
            val sound = WorkSound(f.fixture.context)
            val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
            val engine = TemporaryEngine(f.newStore()) { type ->
                assertFalse("Feedback must run after the save transaction", f.db.inTransaction())
                if (type == "SAVED") assertEquals(1L, f.store.total(f.id).count)
                sound.play(type); calls.add(type)
            }
            try {
                engine.resume(f.id); await(engine) { it.ready && it.queued == 0 }
                assertTrue(engine.receive(f.barcodes[0], "BROADCAST"))
                val saved = await(engine) { it.total.count == 1L && it.queued == 0 }
                assertTrue(saved.ready); assertTrue(saved.message.contains("不得放行"))
                assertEquals(listOf("SAVED"), calls.toList())
                assertEquals("声音未就绪，请以页面保存状态为准", sound.error)
                val original = f.db.temporaryScans().captures(f.id).single()
                assertEquals("COMMITTED", original.state)
                engine.close(); f.reopen(); f.processor.drain(f.id)
                assertEquals(original, f.db.temporaryScans().captures(f.id).single())
                assertEquals(1L, f.store.total(f.id).count); assertTrue(f.feedback.isEmpty())
                assertNull(f.db.temporarySubmissions().forOrder(f.id))
            } finally { engine.close() }
        }
    }

    private suspend fun await(engine: TemporaryEngine, predicate: (TemporaryWorkUi) -> Boolean): TemporaryWorkUi =
        withTimeout(15000) { engine.state.first(predicate) }
}
