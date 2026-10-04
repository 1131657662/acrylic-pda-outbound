package com.acrylic.outbound.pda.continuation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class HandoverTargetAssignmentVerifierTest {
    private val fixture = HandoverPackageFixture()
    private val now = Instant.parse("2026-10-04T01:00:00Z")

    @Test fun signedTargetAssignmentMatchesActivatedHandover() {
        val stage = stage(activated = true)
        val target = fixture.targetAssignment()

        val verified = HandoverTargetAssignmentVerifier.verify(stage, target, fixture.session, now)

        assertEquals(fixture.targetAssignmentId, verified.getString("id"))
    }

    @Test fun signedAssignmentForAnotherTargetIsRejected() {
        val target = fixture.targetAssignment(id = UUID.randomUUID().toString())

        assertThrows(IllegalStateException::class.java) {
            HandoverTargetAssignmentVerifier.verify(stage(true), target, fixture.session, now)
        }
    }

    @Test fun signedAssignmentForAnotherOrderIsRejected() {
        val target = fixture.targetAssignment(orderId = UUID.randomUUID().toString())

        assertThrows(IllegalStateException::class.java) {
            HandoverTargetAssignmentVerifier.verify(stage(true), target, fixture.session, now)
        }
    }

    @Test fun signedActivationEpochMustMatchTargetCredential() {
        val (manifest, bytes) = fixture.packageBytes(activated = true)
        val receipt = manifest.getJSONObject("activationReceipt")
        val decision = JSONObject(receipt.getJSONObject("businessDecision").toString())
            .put("assignmentEpoch", "3")
        manifest.put("activationReceipt", fixture.activation(receipt.getJSONObject("caseContext"), decision))
        val stage = HandoverPackageVerifier.verify(manifest, bytes, fixture.session)

        assertThrows(IllegalStateException::class.java) {
            HandoverTargetAssignmentVerifier.verify(stage, fixture.targetAssignment(), fixture.session, now)
        }
    }

    @Test fun changedAssignmentAfterSigningIsRejected() {
        val target = fixture.targetAssignment().put("startOrderScanSeq", "9")

        assertThrows(IllegalStateException::class.java) {
            HandoverTargetAssignmentVerifier.verify(stage(true), target, fixture.session, now)
        }
    }

    @Test fun expiredOfflineCredentialIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            HandoverTargetAssignmentVerifier.verify(stage(true), fixture.targetAssignment(),
                fixture.session, Instant.parse("2026-10-06T00:00:00Z"))
        }
    }

    @Test fun preparedHandoverCannotImportTargetCredential() {
        assertThrows(IllegalStateException::class.java) {
            HandoverTargetAssignmentVerifier.verify(stage(false), fixture.targetAssignment(), fixture.session, now)
        }
    }

    @Test fun alteredLocalRecoveryBytesAreRejected() {
        val damaged = stage(true).copy(recoveryBytes = byteArrayOf(1))

        assertThrows(Exception::class.java) {
            HandoverTargetAssignmentVerifier.verify(damaged, fixture.targetAssignment(), fixture.session, now)
        }
    }

    @Test fun changedSessionIdentityIsRejected() {
        val other = JSONObject(fixture.session.toString()).put("userId", UUID.randomUUID().toString())

        assertThrows(IllegalStateException::class.java) {
            HandoverTargetAssignmentVerifier.verify(stage(true), fixture.targetAssignment(), other, now)
        }
    }

    private fun stage(activated: Boolean): LocalHandoverStage {
        val (manifest, bytes) = fixture.packageBytes(activated)
        return HandoverPackageVerifier.verify(manifest, bytes, fixture.session)
    }
}
