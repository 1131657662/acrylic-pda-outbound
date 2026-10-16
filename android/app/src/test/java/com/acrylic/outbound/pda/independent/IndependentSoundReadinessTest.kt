package com.acrylic.outbound.pda.independent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.device.WorkSound
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentSoundReadinessTest {
    @Test fun unloadedSoundKeepsIndependentSavePendingReconciliationAfterReopen() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            val sound = WorkSound(ApplicationProvider.getApplicationContext<Context>())
            val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
            val environment = f.environment.copy(feedback = { type ->
                assertFalse("Feedback must run after the save transaction", f.task.sku.db.inTransaction())
                if (type == "SAVED") assertEquals(1L, f.store.dao.total(f.command.id).count)
                sound.play(type); calls.add(type)
            })
            val engine = IndependentScanEngine(f.task.sku.db, environment) { f.task.grant() }
            try {
                engine.resume(f.command.id); await(engine) { it.task != null && it.queued == 0 }
                engine.authorize(f.command.id, f.task.identity.password); await(engine) { it.ready && it.queued == 0 }
                assertTrue(engine.receive("003A0000001", "BROADCAST"))
                val saved = await(engine) { it.rows.size == 1 && it.queued == 0 }
                assertTrue(saved.ready); assertTrue(saved.message.contains("待主管对账"))
                assertEquals(listOf("SAVED"), calls.toList())
                assertEquals("声音未就绪，请以页面保存状态为准", sound.error)
                val original = f.store.dao.captures(f.command.id).single()
                engine.close(); f.reopen()
                val replay = mutableListOf<String>()
                IndependentScanProcessor(f.store) { replay.add(it) }.drain(f.command.id) { true }
                assertEquals(original, f.store.dao.captures(f.command.id).single())
                assertEquals(1L, f.store.dao.total(f.command.id).count); assertTrue(replay.isEmpty())
            } finally { engine.close() }
        }
    }

    private suspend fun await(engine: IndependentScanEngine, predicate: (IndependentWorkUi) -> Boolean): IndependentWorkUi =
        withTimeout(15000) { engine.state.first(predicate) }
}
