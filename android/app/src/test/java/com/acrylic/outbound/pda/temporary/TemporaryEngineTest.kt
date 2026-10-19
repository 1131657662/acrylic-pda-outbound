package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.update.UpdateGate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryEngineTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun foregroundLeaseBlocksInstallerAndBackgroundThenReleasesOnPause() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val engine = TemporaryEngine(f.newStore()) {}
            try {
                engine.resume(f.id); withTimeout(10000) { engine.state.first { it.ready && it.queued == 0 } }
                assertThrows(Exception::class.java) { UpdateGate.beginInstall() }
                assertThrows(Exception::class.java) { UpdateGate.enterBackground() }
                engine.pause(); UpdateGate.enterBackground().close()
                assertFalse(engine.receive(f.barcodes[0], "BROADCAST"))
            } finally { engine.close() }
        }
    }
    @Test fun durableAutomaticInputRecoversThroughApplicationWriter() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val row = f.receive(); f.reopen()
            val sounds = java.util.concurrent.CopyOnWriteArrayList<String>(); val engine = TemporaryEngine(f.newStore()) { sounds.add(it) }
            try {
                engine.resume(f.id)
                val state = withTimeout(10000) { engine.state.first { it.ready && it.queued == 0 && it.total.count == 1L } }
                assertEquals(row.orderScanSeq, state.recent.single().orderScanSeq)
                assertEquals(listOf("SAVED"), sounds.toList()); assertTrue(state.message.contains("不得放行"))
            } finally { engine.close() }
        }
    }
    @Test fun acknowledgingFailureReplacesPromptAndNextQueuedFailureRemainsActionable() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val engine = TemporaryEngine(f.newStore()) {}
            try {
                engine.resume(f.id)
                withTimeout(10000) { engine.state.first { it.ready && it.queued == 0 } }
                assertTrue(engine.receive("bad", "BROADCAST"))
                val first = withTimeout(10000) { engine.state.first { it.candidate?.state == "INVALID" && it.queued == 0 } }.candidate!!
                assertTrue(engine.receive("also-bad", "BROADCAST"))
                withTimeout(10000) { engine.state.first { it.queued == 0 } }
                engine.acknowledge(first.id)
                val next = withTimeout(10000) { engine.state.first { it.candidate?.id != first.id && it.candidate?.state == "INVALID" && it.queued == 0 } }
                assertEquals("本包未保存，请确认处理结果", next.message)
                assertNotNull(f.db.temporaryScans().capture(first.id)?.failureAckAt)
                engine.acknowledge(next.candidate!!.id)
                val done = withTimeout(10000) { engine.state.first { it.candidate == null && it.queued == 0 } }
                assertEquals("已确认本包未保存，原输入保留", done.message)
                assertEquals(0L, done.total.count)
                assertEquals(2, f.db.temporaryScans().captures(f.id).size)
                assertNotNull(f.db.temporaryScans().capture(next.candidate.id)?.failureAckAt)
            } finally { engine.close() }
        }
    }

    @Test fun fullInputQueueShowsOnlyAcceptedInputs() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val engine = TemporaryEngine(f.newStore()) {}
            try {
                engine.resume(f.id)
                withTimeout(10000) { engine.state.first { it.ready && it.queued == 0 } }
                f.fixture.blockNow = true
                assertTrue(engine.receive(f.barcodes[0], "BROADCAST"))
                assertTrue(f.fixture.nowEntered.await(10, TimeUnit.SECONDS))
                repeat(64) { assertTrue(engine.receive(f.barcodes[0], "BROADCAST")) }
                assertFalse(engine.receive(f.barcodes[0], "BROADCAST"))
                assertEquals(65, engine.state.value.queued)
                assertTrue(engine.state.value.blocked.contains("入口已满"))
                f.fixture.releaseNow.countDown()
                withTimeout(10000) { engine.state.first { it.queued == 0 } }
                assertEquals(65, f.db.temporaryScans().captures(f.id).size)
            } finally {
                f.fixture.releaseNow.countDown()
                engine.close()
            }
        }
    }
}
