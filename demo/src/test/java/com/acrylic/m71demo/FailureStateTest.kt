package com.acrylic.m71demo

import android.app.Application
import com.acrylic.m71demo.device.*
import com.acrylic.m71demo.workflow.ConfirmationService
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class FailureStateTest {
    private lateinit var rig: TestRig
    @Before fun setup() { rig = TestRig(planned = 1) }
    @After fun teardown() { rig.close() }

    @Test fun confirmedCandidateLosingLastSlotReachesBusinessRejection() {
        val a = rig.receive("003A0000001")
        val b = rig.receive("003A0000002")
        rig.confirmation.request(a.id, rig.clock.elapsedNs())
        rig.confirmation.request(b.id, rig.clock.elapsedNs())
        rig.confirmation.save(a.id)
        assertEquals("BUSINESS_REJECTED", rig.confirmation.save(b.id).status)
        rig.correction.acknowledge(b.id)
        assertNull(rig.db.captures().failure("order"))
        assertEquals(0, rig.db.captures().pendingCount("order"))
    }

    @Test fun oldCycleIsNeverAutomaticallyUpgraded() {
        val c = rig.receive("003A0000001")
        rig.db.inventory().simulateCycleChange(c.context.barcode)
        rig.confirmation.request(c.id, rig.clock.elapsedNs())
        assertEquals("DISPATCH_CYCLE_CONFLICT", rig.confirmation.save(c.id).error)
        assertEquals(1L, rig.db.captures().get(c.id)!!.context.expectedDispatchCycle)
        assertEquals(0, rig.db.scans().count("order"))
    }

    @Test fun storageFailureRollsBackWholeSaveAndRetainsIntent() {
        val c = rig.receive("003A0000001")
        val fault = FaultGate(Telemetry(rig.context)).apply { point = "DURING_SAVE" }
        val service = ConfirmationService(rig.store, fault)
        service.request(c.id, rig.clock.elapsedNs())
        assertThrows(java.io.IOException::class.java) { service.save(c.id) }
        assertNull(rig.db.scans().get(c.id))
        assertNull(rig.db.scans().guard(c.context.barcode))
        assertEquals(0, rig.db.orders().get("order")!!.actualCount)
        assertEquals("CONFIRM_REQUESTED", rig.db.captures().get(c.id)!!.state)
        assertEquals("SAVED", service.save(c.id).status)
    }

    @Test fun confirmedCaptureCannotBeDiscarded() {
        val c = rig.receive("003A0000001")
        rig.confirmation.request(c.id, rig.clock.elapsedNs())
        assertThrows(IllegalStateException::class.java) { rig.correction.discard(c.id) }
        assertEquals("CONFIRM_REQUESTED", rig.db.captures().get(c.id)!!.state)
    }

    @Test fun expiredAuthorizationCannotSaveConfirmedCandidate() {
        val c = rig.receive("003A0000001")
        rig.confirmation.request(c.id, rig.clock.elapsedNs())
        rig.clock.elapsed += 49L * 60 * 60 * 1_000_000_000
        assertEquals("AUTH_EXPIRED", rig.confirmation.save(c.id).error)
    }

    @Test fun differentOriginsCanHaveSameDeviceSequence() {
        val a = rig.receive("003A0000001")
        val b = a.copy(id = "imported-B", context = a.context.copy(originDeviceId = "device-B", orderScanSeq = 2))
        rig.db.captures().insert(b)
        assertEquals(2, rig.db.captures().count("order"))
        assertEquals("device-A", rig.db.captures().get(a.id)!!.context.originDeviceId)
        assertEquals("device-B", rig.db.captures().get(b.id)!!.context.originDeviceId)
    }
}
