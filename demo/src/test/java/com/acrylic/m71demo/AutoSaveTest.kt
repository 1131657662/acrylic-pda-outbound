package com.acrylic.m71demo

import com.acrylic.m71demo.device.*
import com.acrylic.m71demo.workflow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AutoSaveTest {
    private fun processor(rig: TestRig, fault: FaultGate? = null): CaptureProcessor {
        val telemetry = Telemetry(rig.context)
        val gate = fault ?: FaultGate(telemetry)
        return CaptureProcessor(rig.store, WorkflowServices(rig.capture,
            ConfirmationService(rig.store, gate), rig.correction),
            DemoHardware(SoundFeedback(rig.context, telemetry), telemetry, gate))
    }

    private fun input(rig: TestRig, code: String = "003A0000001", source: String = "BROADCAST") =
        IncomingBarcode(java.util.UUID.randomUUID().toString(), "order", code, source,
            rig.clock.wallMs(), rig.clock.elapsedNs(), rig.clock.bootId())

    @Test fun broadcastCommitsWithoutEnterAndKeepsPolicy() = TestRig().use { rig ->
        val barcode = input(rig)
        processor(rig).receive(barcode)
        assertEquals("COMMITTED", rig.db.captures().get(barcode.id)!!.state)
        assertEquals("AUTO_AFTER_VALIDATION", rig.db.scans().get(barcode.id)!!.context.confirmationPolicy)
        assertEquals(300500L, rig.db.orders().get("order")!!.weightGrams)
    }

    @Test fun manualRequiresExplicitConfirmation() = TestRig().use { rig ->
        val barcode = input(rig, source = "MANUAL")
        val worker = processor(rig)
        worker.receive(barcode)
        assertEquals("WAITING_CONFIRM", rig.db.captures().get(barcode.id)!!.state)
        assertEquals(0, rig.db.scans().count("order"))
        worker.confirm(barcode.id, rig.clock.elapsedNs())
        assertEquals(1, rig.db.scans().count("order"))
    }

    @Test fun duplicateDoesNotIncreaseTotals() = TestRig().use { rig ->
        val worker = processor(rig)
        worker.receive(input(rig))
        val duplicate = input(rig)
        worker.receive(duplicate)
        assertEquals("INVALID", rig.db.captures().get(duplicate.id)!!.state)
        assertEquals(1, rig.db.orders().get("order")!!.actualCount)
    }

    @Test fun errorPausesLaterInputsUntilAcknowledged() = TestRig().use { rig ->
        val worker = processor(rig)
        val bad = input(rig, "BAD")
        val first = input(rig)
        val second = input(rig, "003A0000002")
        worker.receive(bad); worker.receive(first); worker.receive(second)
        assertEquals("RECEIVED", rig.db.captures().get(second.id)!!.state)
        assertEquals(0, rig.db.scans().count("order"))
        rig.correction.acknowledge(bad.id)
        worker.drain("order")
        assertEquals(2, rig.db.scans().count("order"))
        assertEquals(2L, rig.db.scans().get(first.id)!!.context.orderScanSeq)
        assertEquals(3L, rig.db.scans().get(second.id)!!.context.orderScanSeq)
    }

    @Test fun durableAutoIntentSurvivesRestartExactlyOnce() = TestRig().use { rig ->
        val barcode = input(rig)
        rig.capture.receive(barcode)
        rig.reopen(); processor(rig).drain("order")
        rig.reopen(); processor(rig).drain("order")
        assertEquals(1, rig.db.scans().count("order"))
        assertEquals("COMMITTED", rig.db.captures().get(barcode.id)!!.state)
    }

    @Test fun oldBroadcastCandidateIsNeverUpgradedToAuto() = TestRig().use { rig ->
        val capture = rig.receive("003A0000001")
        rig.db.captures().update(capture.copy(context = capture.context.copy(inputSource = "BROADCAST")))
        rig.reopen(); processor(rig).drain("order")
        assertEquals(0, rig.db.scans().count("order"))
        assertEquals("WAITING_CONFIRM", rig.db.captures().get(capture.id)!!.state)
    }

    @Test fun autoCannotOvertakeAnEarlierManualCandidate() = TestRig().use { rig ->
        val worker = processor(rig)
        val manual = input(rig, source = "MANUAL")
        val auto = input(rig, "003A0000002")
        worker.receive(manual); worker.receive(auto)
        assertEquals("RECEIVED", rig.db.captures().get(auto.id)!!.state)
        worker.confirm(manual.id, rig.clock.elapsedNs())
        assertEquals(2, rig.db.scans().count("order"))
        assertTrue(rig.db.scans().get(manual.id)!!.localSeq < rig.db.scans().get(auto.id)!!.localSeq)
    }

    @Test fun storageFailureRollsBackAndRecoversOnlyOnce() = TestRig().use { rig ->
        val gate = FaultGate(Telemetry(rig.context)).apply { point = "DURING_SAVE" }
        val barcode = input(rig)
        assertThrows(java.io.IOException::class.java) { processor(rig, gate).receive(barcode) }
        assertEquals("STORAGE_ERROR", rig.db.captures().get(barcode.id)!!.state)
        assertEquals(0, rig.db.orders().get("order")!!.actualCount)
        assertEquals(0, rig.db.scans().count("order"))
        rig.reopen(); processor(rig).drain("order")
        processor(rig).drain("order")
        assertEquals(1, rig.db.orders().get("order")!!.actualCount)
        assertEquals(1, rig.db.scans().count("order"))
    }

    @Test fun changedCycleRejectsOriginalAutoIntent() = TestRig().use { rig ->
        val barcode = input(rig)
        val capture = rig.capture.receive(barcode)
        rig.db.inventory().simulateCycleChange(barcode.raw)
        processor(rig).drain("order")
        assertEquals("DISPATCH_CYCLE_CONFLICT", rig.db.captures().get(barcode.id)!!.error)
        assertEquals(capture.context.expectedDispatchCycle, rig.db.captures().get(barcode.id)!!.context.expectedDispatchCycle)
        assertEquals(0, rig.db.scans().count("order"))
    }

    @Test fun combinedValidationAndIntentSurviveFailureBeforeFinalSave() = TestRig().use { rig ->
        val gate = FaultGate(Telemetry(rig.context)).apply { point = "AFTER_CONFIRM" }
        val barcode = input(rig)
        assertThrows(java.io.IOException::class.java) { processor(rig, gate).receive(barcode) }
        val durable = rig.db.captures().get(barcode.id)!!
        assertEquals("STORAGE_ERROR", durable.state)
        assertNotNull(durable.confirmedAtNs)
        assertEquals(0, rig.db.scans().count("order"))
        rig.reopen(); processor(rig).drain("order")
        assertEquals(1, rig.db.scans().count("order"))
        assertEquals(durable.confirmedAtNs, rig.db.scans().get(barcode.id)!!.confirmedAtNs)
    }

    @Test fun expiredAuthorizationDoesNotAutoSave() = TestRig().use { rig ->
        rig.clock.elapsed += 49L * 60 * 60 * 1_000_000_000
        rig.clock.wall += 49L * 60 * 60 * 1000
        val barcode = input(rig)
        processor(rig).receive(barcode)
        assertEquals("INVALID", rig.db.captures().get(barcode.id)!!.state)
        assertEquals(0, rig.db.scans().count("order"))
    }

    @Test fun durableAutoIntentCannotBeDiscarded() = TestRig().use { rig ->
        val barcode = input(rig)
        rig.capture.receive(barcode); rig.capture.validate(barcode.id)
        assertThrows(IllegalStateException::class.java) { rig.correction.discard(barcode.id) }
        Unit
    }

    @Test fun syntheticBroadcastIsLabeledAsTest() = TestRig().use { rig ->
        val barcode = input(rig, source = "TEST_INJECTION")
        processor(rig).receive(barcode)
        assertEquals("TEST_INJECTION", rig.db.scans().get(barcode.id)!!.context.inputSource)
        assertEquals("AUTO_AFTER_VALIDATION", rig.db.scans().get(barcode.id)!!.context.confirmationPolicy)
    }

    @Test fun lateEnterForAutoCannotConfirmTheNextManualInput() = TestRig().use { rig ->
        val worker = processor(rig)
        val auto = input(rig)
        val manual = input(rig, "003A0000002", "MANUAL")
        worker.receive(auto); worker.receive(manual)
        worker.confirm(auto.id, rig.clock.elapsedNs())
        assertEquals("WAITING_CONFIRM", rig.db.captures().get(manual.id)!!.state)
        assertEquals(1, rig.db.scans().count("order"))
    }
}
