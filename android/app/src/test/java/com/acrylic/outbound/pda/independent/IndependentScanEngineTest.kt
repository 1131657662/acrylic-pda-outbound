package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.update.UpdateGate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentScanEngineTest {
    @Test fun manualInputSurvivesNewEngineAndOnlyExplicitConfirmationSaves() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            val sounds = java.util.Collections.synchronizedList(mutableListOf<String>())
            val environment = f.environment.copy(feedback = { sounds.add(it) })
            var engine = engine(f, environment); open(engine, f)
            try {
                assertTrue(engine.receive("003A0000001", "MANUAL"))
                await(engine) { it.candidate?.processing?.state == "WAITING_CONFIRM" && it.queued == 0 }
                val id = engine.state.value.candidate!!.capture.id
                assertEquals(0L, f.store.dao.total(f.command.id).count)
                assertTrue(sounds.isEmpty())
                engine.close(); engine = engine(f, environment); open(engine, f, false)
                assertEquals(id, engine.state.value.candidate!!.capture.id)
                engine.confirm(id); await(engine) { it.ready && it.queued == 0 }
                assertEquals(1L, f.store.dao.total(f.command.id).count)
                assertEquals(1, f.store.dao.captures(f.command.id).size)
                assertEquals(listOf("SAVED"), sounds.toList())
            } finally { engine.close() }
        }
    }
    @Test fun boundedQueueRejectsOverflowAndPauseKeepsEveryAcceptedOriginal() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            val block = AtomicBoolean(false); val entered = CountDownLatch(1); val release = CountDownLatch(1)
            val environment = f.environment.copy(actor = {
                if (block.compareAndSet(true, false)) { entered.countDown(); check(release.await(30, TimeUnit.SECONDS)) }
                f.task.actor
            })
            val engine = engine(f, environment); open(engine, f)
            try {
                block.set(true); assertTrue(engine.receive("003A0000001", "BROADCAST"))
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                repeat(64) { assertTrue(engine.receive("003A0000001", "BROADCAST")) }
                assertFalse(engine.receive("003A0000001", "BROADCAST"))
                assertEquals(65, engine.state.value.queued)
                assertTrue(engine.state.value.blocked.contains("入口已满")); assertFalse(engine.state.value.ready)
                engine.pause(); assertThrows(IllegalStateException::class.java) { UpdateGate.beginInstall() }
                release.countDown(); await(engine) { it.queued == 0 && it.candidate != null }
                val captures = f.store.dao.captures(f.command.id)
                assertEquals(65, captures.size); assertEquals((1L..65L).toList(), captures.map { it.capture.sequence })
                assertTrue(captures.all { it.processing.state == "RECEIVED" })
                assertEquals(0L, f.store.dao.total(f.command.id).count)
                UpdateGate.beginInstall(); UpdateGate.endInstall()
            } finally { release.countDown(); engine.close(); UpdateGate.endInstall() }
        }
    }
    @Test fun queueTailFailureRecoversSameInputOnceAfterReauthorization() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            val db = f.task.sku.db.openHelper.writableDatabase
            db.execSQL("""CREATE TRIGGER queue_tail BEFORE INSERT ON independent_task_event
                WHEN NEW.type='INDEPENDENT_SAVED' BEGIN SELECT RAISE(ABORT,'queue tail'); END""")
            val engine = engine(f); open(engine, f)
            try {
                assertTrue(engine.receive("003A0000001", "BROADCAST"))
                await(engine) { it.queued == 0 && it.blocked.contains("queue tail") }
                val id = checkNotNull(engine.state.value.candidate).capture.id
                assertEquals("CONFIRM_REQUESTED", f.store.dao.capture(id)?.processing?.state)
                assertNull(f.store.dao.scan(id)); assertEquals(0L, f.store.dao.total(f.command.id).count)
                db.execSQL("DROP TRIGGER queue_tail"); engine.authorize(f.command.id, f.task.identity.password)
                await(engine) { it.ready && it.queued == 0 }
                assertNotNull(f.store.dao.scan(id)); assertEquals(1, f.store.dao.captures(f.command.id).size)
            } finally { engine.close() }
        }
    }
    @Test fun expiryStopsNewAdmissionWithoutChangingSavedFacts() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            val engine = engine(f); open(engine, f)
            try {
                assertTrue(engine.receive("003A0000001", "BROADCAST")); await(engine) { it.rows.size == 1 && it.queued == 0 }
                val original = f.store.dao.captures(f.command.id)
                f.task.identity.advance(48 * 3600 * 1000L)
                await(engine) { !it.ready && it.blocked.isNotBlank() }
                assertFalse(engine.receive("003A0000001", "BROADCAST"))
                assertEquals(original, f.store.dao.captures(f.command.id))
            } finally { engine.close() }
        }
    }
    private fun engine(f: IndependentScanFixture, environment: IndependentScanEnvironment = f.environment) =
        IndependentScanEngine(f.task.sku.db, environment) { f.task.grant() }
    private suspend fun open(engine: IndependentScanEngine, f: IndependentScanFixture, ready: Boolean = true) {
        engine.resume(f.command.id); await(engine) { it.task != null && it.queued == 0 }
        engine.authorize(f.command.id, f.task.identity.password)
        await(engine) { it.queued == 0 && if (ready) it.ready else it.candidate != null && it.blocked.isEmpty() }
    }
    private suspend fun await(engine: IndependentScanEngine, predicate: (IndependentWorkUi) -> Boolean) =
        withTimeout(15000) { engine.state.first(predicate) }
}
