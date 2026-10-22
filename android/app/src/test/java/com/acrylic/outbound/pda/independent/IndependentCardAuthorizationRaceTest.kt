package com.acrylic.outbound.pda.independent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentCardAuthorizationRaceTest {
    @Test fun leavingAndResumingDuringCardReadRejectsStaleAuthorization() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            val engine = IndependentScanEngine(f.task.sku.db, f.environment) { f.task.grant() }
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            try {
                engine.resume(f.command.id); await(engine) { it.task != null && it.queued == 0 }
                engine.authorizeCard(f.created.task) {
                    entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); f.task.grant()
                }
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                engine.pause(); engine.resume(f.command.id); release.countDown()
                await(engine) { it.queued == 0 && it.blocked.contains("页面已离开") }
                assertFalse(engine.state.value.ready)
                assertFalse(engine.receive("003A0000001", "BROADCAST"))
                assertEquals("PLANNED", f.task.sku.db.independentTasks().state(f.command.id)?.state)
                assertTrue(f.store.dao.captures(f.command.id).isEmpty())
            } finally { release.countDown(); engine.close() }
        }
    }

    @Test fun changedOriginalTaskSnapshotRejectsBeforeOpeningCard() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            val engine = IndependentScanEngine(f.task.sku.db, f.environment) { f.task.grant() }
            var opened = false
            try {
                engine.resume(f.command.id); await(engine) { it.task != null && it.queued == 0 }
                engine.authorizeCard(f.created.task.copy(commandSha256 = "0".repeat(64))) {
                    opened = true; f.task.grant()
                }
                await(engine) { it.queued == 0 && it.blocked.contains("授权已变化") }
                assertFalse(opened); assertFalse(engine.state.value.ready)
                assertEquals(f.created.task, f.task.sku.db.independentTasks().task(f.command.id))
                assertEquals(1, f.task.sku.db.independentTasks().events(f.command.id).size)
            } finally { engine.close() }
        }
    }

    private suspend fun await(engine: IndependentScanEngine, predicate: (IndependentWorkUi) -> Boolean) =
        withTimeout(15000) { engine.state.first(predicate) }
}
