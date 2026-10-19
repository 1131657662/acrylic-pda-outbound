package com.acrylic.outbound.pda.independent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentRecentEngineTest {
    @Test fun latestSnapshotSurvivesConfirmationPagingVoidAndNewEngine() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            var engine = engine(f)
            try {
                open(engine, f)
                assertTrue(engine.receive("003A0000001", "MANUAL"))
                await(engine) { it.queued == 0 && it.candidate?.processing?.state == "WAITING_CONFIRM" }
                assertNull(engine.state.value.latestSaved)
                val id = engine.state.value.candidate!!.capture.id
                engine.confirm(id); await(engine) { it.queued == 0 && it.latestSaved?.scan?.captureId == id }
                assertEquals(1L, engine.state.value.task?.state?.actualCount)
                engine.page(true); await(engine) { it.queued == 0 && it.rows.isEmpty() && it.after == 1L }
                assertEquals(id, engine.state.value.latestSaved?.scan?.captureId)
                engine.pause(); engine.voidSelected(id, "核实未装车")
                await(engine) { it.queued == 0 && it.latestSaved?.status == "VOID" }
                assertEquals(0L, engine.state.value.task?.state?.actualCount)
                engine.close(); f.reopen(); engine = engine(f); open(engine, f)
                assertEquals(id, engine.state.value.latestSaved?.scan?.captureId)
                assertEquals("VOID", engine.state.value.latestSaved?.status)
            } finally { engine.close() }
        }
    }

    @Test fun switchingToAnotherOwnedTaskClearsPreviousSavedSnapshot() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); val original = f.save()
            val next = f.task.command(); f.task.repository.create(next, f.grant)
            val engine = engine(f)
            try {
                open(engine, f)
                assertEquals(original.capture.id, engine.state.value.latestSaved?.scan?.captureId)

                engine.pause(); engine.resume(next.id)
                await(engine) { it.queued == 0 && it.task?.task?.id == next.id }

                assertNull(engine.state.value.latestSaved)
                assertEquals(0L, engine.state.value.task?.state?.actualCount)
                assertTrue(engine.state.value.rows.isEmpty())
            } finally { engine.close() }
        }
    }

    private fun engine(f: IndependentScanFixture) = IndependentScanEngine(f.task.sku.db, f.environment) { f.task.grant() }
    private suspend fun open(engine: IndependentScanEngine, f: IndependentScanFixture) {
        engine.resume(f.command.id); await(engine) { it.task != null && it.queued == 0 }
        engine.authorize(f.command.id, f.task.identity.password)
        await(engine) { it.queued == 0 && it.ready }
    }
    private suspend fun await(engine: IndependentScanEngine, predicate: (IndependentWorkUi) -> Boolean) =
        withTimeout(15000) { engine.state.first(predicate) }
}
