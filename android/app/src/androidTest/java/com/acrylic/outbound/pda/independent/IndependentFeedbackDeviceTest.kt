package com.acrylic.outbound.pda.independent

import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.TimeSample
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/** A native database save survives an injected failure at the feedback boundary. */
class IndependentFeedbackDeviceTest {
    @Test fun committedInputRecoversWithoutRepeatingFeedbackAfterCallbackFailure() {
        check(InstrumentationRegistry.getArguments().getString("feedbackBoundary") == "true")
        NativeIndependentQueueFixture().use { f ->
            f.engine.close()
            val probe = NativeFeedbackProbe(f)
            f.engine = probe.engine()
            f.open()
            assertEquals("WAITING_CONFIRM", f.engine.state.value.candidate?.processing?.state)
            f.engine.confirm(f.capture.id)
            f.await { f.engine.state.value.queued == 0 && f.engine.state.value.blocked.contains("injected feedback unavailable") }
            assertFalse(f.engine.state.value.ready)
            assertEquals(listOf("SAVED"), probe.calls.toList())
            probe.committed()
            probe.record("feedback-failed")
            f.engine.pause()
            assertFalse(f.engine.receive(f.capture.rawInput, "BROADCAST"))
            f.restart()
            f.await { f.engine.state.value.ready && f.engine.state.value.queued == 0 }
            probe.committed()
            assertEquals(listOf("SAVED"), probe.calls.toList())
            assertTrue(f.feedback.isEmpty())
            probe.record("feedback-recovered")
            f.record("feedback-recovered")
        }
    }
}

private class NativeFeedbackProbe(private val fixture: NativeIndependentQueueFixture) {
    val calls = ConcurrentLinkedQueue<String>()

    fun engine(): IndependentScanEngine {
        val task = fixture.task
        val snapshot = IndependentCredentialSnapshot(IndependentCredentialProof.verify(task.signedCredential,
            JSONObject(task.trustedKeyJson)), task.trustedKeyJson, task.timeAnchorJson, task.localSessionId)
        val environment = IndependentScanEnvironment(sample = {
            val anchor = JSONObject(task.timeAnchorJson)
            TimeSample(anchor.getLong("elapsed"), anchor.getLong("wall"), anchor.getInt("boot"))
        }, actor = { IndependentTaskActor(task.operatorId, task.deviceId, task.warehouseId) }, feedback = {
            assertFalse(fixture.db.inTransaction())
            assertEquals("SAVED", it)
            committed()
            calls.add(it)
            throw IllegalStateException("injected feedback unavailable")
        })
        return IndependentScanEngine(fixture.db, environment) { IndependentCaptureGrant(snapshot.proof) { snapshot } }
    }

    fun committed() {
        val f = fixture
        assertEquals(listOf(f.capture), f.db.independentScans().captures(f.task.id).map { it.capture })
        assertEquals("COMMITTED", f.db.independentScans().capture(f.capture.id)?.processing?.state)
        assertEquals(f.capture.id, f.db.independentScans().scan(f.capture.id)?.captureId)
        assertEquals(f.capture.id, f.db.independentScans().guard(f.capture.barcode)?.captureId)
        assertEquals(1L, f.db.independentScans().total(f.task.id).count)
    }

    fun record(phase: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val run = checkNotNull(InstrumentationRegistry.getArguments().getString("evidenceRun"))
        val target = File(context.filesDir, "independent-queue-evidence/$run/$phase.json")
        check(target.createNewFile())
        val state = fixture.engine.state.value
        target.writeText(JSONObject().put("syntheticOnly", true).put("phase", phase)
            .put("captureId", fixture.capture.id).put("saved", fixture.db.independentScans().total(fixture.task.id).count)
            .put("queued", state.queued).put("ready", state.ready).put("blocked", state.blocked)
            .put("message", state.message).put("callbacks", JSONArray(calls.toList()))
            .put("replayedFeedback", JSONArray(fixture.feedback.toList())).toString(2))
    }
}
