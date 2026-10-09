package com.acrylic.outbound.pda.continuation

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class HandoverFormalFaultTest {
    private val f = HandoverFormalFaultFixture()

    @Test fun preparedFaultStagesOriginalWithoutStopAcknowledgement() {
        val (manifest, bytes) = f.packageBytes()
        val stage = HandoverPackageVerifier.verify(manifest, bytes, f.session)
        assertEquals(f.normal.assignment, stage.originalAssignmentId)
        assertArrayEquals(bytes, stage.recoveryBytes)
        assertFalse(JSONObject(stage.manifestJson).has("stopReceipt"))
        assertFalse(manifest.getJSONObject("faultReceipt").getJSONObject("businessDecision").getBoolean("oldDeviceAcknowledged"))
    }

    @Test fun activatedFaultConsumesNamedTargetCredential() {
        val (manifest, bytes) = f.packageBytes(activated = true)
        val stage = HandoverPackageVerifier.verify(manifest, bytes, f.session)
        val target = f.normal.targetAssignment()
        assertEquals(f.normal.targetAssignmentId, HandoverTargetAssignmentVerifier.verify(
            stage, target, f.session, Instant.parse("2026-10-05T00:00:00Z")).getString("id"))
    }

    @Test fun signedFalseAcknowledgementIsRejected() = refused { receipt ->
        receipt.getJSONObject("businessDecision").put("oldDeviceAcknowledged", true)
    }

    @Test fun signedSourceOperatorCannotApproveOwnFault() = refused { receipt ->
        receipt.getJSONObject("actor").put("principalId", receipt.getJSONObject("businessDecision")
            .getJSONObject("source").getString("operatorId"))
    }

    @Test fun signedRequesterCannotApproveOwnFault() = refused { receipt ->
        receipt.getJSONObject("actor").put("principalId", receipt.getJSONObject("caseContext").getString("requestedBy"))
    }

    @Test fun signedUncontrolledDeviceIsRejected() = refused { receipt ->
        receipt.getJSONObject("businessDecision").getJSONObject("physicalControl").put("deviceState", "OFFLINE_UNKNOWN")
    }

    @Test fun signedMissingPermissionIsRejected() = refused { receipt ->
        receipt.getJSONObject("actor").getJSONObject("permissionSnapshot").put("effectivePermissions", org.json.JSONArray())
    }

    @Test fun signedArchiveOtherAssignmentIsRejected() = refused { receipt ->
        receipt.getJSONObject("businessDecision").getJSONObject("archivedEvidence").put("assignmentId", UUID.randomUUID().toString())
    }

    @Test fun signedArchiveOtherHighWatermarkIsRejected() = refused { receipt ->
        receipt.getJSONObject("businessDecision").getJSONObject("archivedEvidence").put("orderScanHighWatermark", "8")
    }

    @Test fun signedArchiveOtherAuditHighIsRejected() = refused { receipt ->
        receipt.getJSONObject("businessDecision").getJSONObject("archivedEvidence").put("auditThroughSeq", "32")
    }

    @Test fun faultCannotContainSourceStopReceipt() {
        val (manifest, bytes) = f.packageBytes()
        manifest.put("stopReceipt", f.normal.packageBytes().first.getJSONObject("stopReceipt"))
        assertThrows(Exception::class.java) { HandoverPackageVerifier.verify(manifest, bytes, f.session) }
    }

    @Test fun faultSignatureCannotBeChanged() {
        val (manifest, bytes) = f.packageBytes()
        manifest.getJSONObject("faultReceipt").put("recordedAt", "2026-10-04T00:00:00Z")
        assertThrows(Exception::class.java) { HandoverPackageVerifier.verify(manifest, bytes, f.session) }
    }

    private fun refused(edit: (JSONObject) -> Unit) {
        val (manifest, bytes) = f.packageBytes()
        val receipt = manifest.getJSONObject("faultReceipt")
        edit(receipt)
        manifest.put("faultReceipt", f.sign(receipt))
        assertThrows(Exception::class.java) { HandoverPackageVerifier.verify(manifest, bytes, f.session) }
    }
}
