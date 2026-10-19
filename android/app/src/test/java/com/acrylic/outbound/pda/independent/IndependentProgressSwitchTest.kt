package com.acrylic.outbound.pda.independent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentProgressSwitchTest {
    @Test fun taskSwitchDuringSnapshotCannotRepublishPreviousTask() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); f.save()
            val next = f.task.command(); f.task.repository.create(next, f.grant)
            val gate = ReadGate()
            val environment = f.environment.copy(actor = { gate.beforeRead(); f.task.actor })
            val engine = IndependentScanEngine(f.task.sku.db, environment) { f.task.grant() }
            try {
                engine.resume(f.command.id)
                await(engine) { it.queued == 0 && it.task?.task?.id == f.command.id }
                assertNotNull(engine.state.value.latestSaved)
                gate.arm(); engine.page(false); gate.awaitOldRead()

                engine.pause(); engine.resume(next.id)
                gate.releaseOldRead(); gate.awaitNewRead()

                assertNull("old snapshot reappeared after selecting another task", engine.state.value.task)
                assertNull(engine.state.value.latestSaved)
                gate.releaseNewRead()
                await(engine) { it.queued == 0 && it.task?.task?.id == next.id }
                assertNull(engine.state.value.latestSaved)
            } finally { gate.releaseOldRead(); gate.releaseNewRead(); engine.close() }
        }
    }

    private suspend fun await(engine: IndependentScanEngine, predicate: (IndependentWorkUi) -> Boolean) =
        withTimeout(15000) { engine.state.first(predicate) }

    private class ReadGate {
        private val step = AtomicInteger(-1)
        private val oldEntered = CountDownLatch(1)
        private val oldReleased = CountDownLatch(1)
        private val newEntered = CountDownLatch(1)
        private val newReleased = CountDownLatch(1)
        fun arm() { step.set(0) }
        fun beforeRead() {
            if (step.compareAndSet(0, 1)) { oldEntered.countDown(); check(oldReleased.await(15, TimeUnit.SECONDS)) }
            else if (step.compareAndSet(1, 2)) { newEntered.countDown(); check(newReleased.await(15, TimeUnit.SECONDS)) }
        }
        fun awaitOldRead() { assertTrue(oldEntered.await(15, TimeUnit.SECONDS)) }
        fun awaitNewRead() { assertTrue(newEntered.await(15, TimeUnit.SECONDS)) }
        fun releaseOldRead() { oldReleased.countDown() }
        fun releaseNewRead() { newReleased.countDown() }
    }
}
