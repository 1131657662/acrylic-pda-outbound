package com.acrylic.outbound.pda.independent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentClosureEngineTest {
    @Test fun collectionMustPauseBeforeSerialClosureAndCannotResumeAfterward() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            val engine = IndependentScanEngine(f.db, f.scan.environment.copy(closureSigner = { f.signer })) { f.scan.task.grant() }
            try {
                engine.resume(f.target.localOrderId); await(engine) { it.task != null && it.queued == 0 }
                engine.authorize(f.target.localOrderId, f.scan.task.identity.password); await(engine) { it.ready && it.queued == 0 }
                engine.finish("短装", true)
                assertEquals("SCANNING", f.db.independentTasks().state(f.target.localOrderId)!!.state)
                engine.pause(); engine.finish("短装", true); await(engine) { it.task?.state?.state == "SEALED" && it.queued == 0 }
                assertFalse(engine.receive("003A0000001", "BROADCAST"))
                engine.resume(f.target.localOrderId); await(engine) { it.queued == 0 && !it.ready }
                assertFalse(engine.scanning); assertEquals("SEALED", f.db.independentTasks().state(f.target.localOrderId)!!.state)
            } finally { engine.close() }
        }
    }
    private suspend fun await(engine: IndependentScanEngine, predicate: (IndependentWorkUi) -> Boolean) =
        withTimeout(15000) { engine.state.first(predicate) }
}
