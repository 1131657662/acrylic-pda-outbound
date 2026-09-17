package com.acrylic.outbound.pda.work

import com.acrylic.outbound.pda.data.PreparedOrder
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class WorkEngineTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun fullIngressQueueStopsSavingAndReportsTheUnacceptedInputWithoutDroppingAcceptedInputs() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val entered = CountDownLatch(1); val release = CountDownLatch(1); val hold = AtomicBoolean(false)
            val access = object : WorkAccess by f.access {
                override fun authorize(order: PreparedOrder): java.time.Instant {
                    if (hold.compareAndSet(true, false)) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
                    return f.access.authorize(order)
                }
            }
            val engine = WorkEngine(WorkStore(f.db, access)) {}
            try {
                engine.resume(f.assignment); await { engine.state.value.ready }
                hold.set(true); assertTrue(engine.receive(f.barcodes[0], "BROADCAST")); assertTrue(entered.await(5, TimeUnit.SECONDS))
                repeat(64) { assertTrue(engine.receive(f.barcodes[0], "BROADCAST")) }
                assertFalse(engine.receive(f.barcodes[0], "BROADCAST"))
                assertTrue(engine.state.value.blocked.contains("入口已满")); release.countDown()
                await { engine.state.value.queued == 0 }
                assertEquals(65, f.db.captures().pendingCount(f.assignment))
                assertEquals(0, f.db.scans().total(f.assignment).count)
            } finally { release.countDown(); engine.close() }
        }
    }
    @Test fun switchingOrdersKeepsAcceptedInputOnOriginalOrderUntilExplicitRecovery() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val entered = CountDownLatch(1); val release = CountDownLatch(1); val hold = AtomicBoolean(false)
            val access = object : WorkAccess by f.access {
                override fun authorize(order: PreparedOrder): java.time.Instant {
                    if (hold.compareAndSet(true, false)) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
                    return f.access.authorize(order)
                }
            }
            val engine = WorkEngine(WorkStore(f.db, access)) {}
            try {
                engine.resume(f.assignment); await { engine.state.value.ready }
                hold.set(true); assertTrue(engine.receive(f.barcodes[0], "BROADCAST")); assertTrue(entered.await(5, TimeUnit.SECONDS))
                engine.resume("unprepared-order"); release.countDown(); await { engine.state.value.queued == 0 }
                assertEquals(1, f.db.captures().pendingCount(f.assignment))
                assertEquals(0, f.db.scans().total(f.assignment).count)
                assertNull(engine.state.value.order); assertFalse(engine.state.value.ready)
                assertTrue(engine.state.value.blocked.isNotEmpty())
                engine.resume(f.assignment); await { engine.state.value.queued == 0 }
                assertEquals(0, f.db.scans().total(f.assignment).count)
                engine.recover(); await { engine.state.value.ready && engine.state.value.order?.actualCount == 1L }
                assertEquals(f.barcodes[0], f.db.scans().page(f.assignment, 0, 10).single().barcode)
            } finally { release.countDown(); engine.close() }
        }
    }
    private suspend fun await(condition: () -> Boolean) {
        withTimeout(10000) { while (!condition()) delay(10) }
    }
}
