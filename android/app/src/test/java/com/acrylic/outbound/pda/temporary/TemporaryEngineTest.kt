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
}
