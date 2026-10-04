package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.time.Instant

/** Binds a live target credential to the immutable, signed source evidence on this PDA. */
internal object HandoverTargetAssignmentVerifier {
    fun verify(stage: LocalHandoverStage, assignment: JSONObject, session: JSONObject, now: Instant): JSONObject {
        val manifest = JSONObject(stage.manifestJson)
        val verified = HandoverPackageVerifier.verify(manifest, stage.recoveryBytes, session)
        check(sameHandoverEvidence(stage.copy(state = "STAGED"), verified)) { "本机交接暂存与签名证据不一致" }
        check(manifest.getString("status") == "ACTIVATED") { "交接尚未激活，不能导入目标授权" }
        JwtVerifier.authorization(assignment, session, session.getJSONObject("signingKey"), now)

        val decision = manifest.getJSONObject("activationReceipt").getJSONObject("businessDecision")
        val source = manifest.getJSONObject("stopReceipt").getJSONObject("source")
        val next = Math.addExact(stage.inputHighWatermark, 1).toString()
        check(assignment.getString("id") == decision.getString("assignmentId") &&
            assignment.getString("id") != stage.originalAssignmentId &&
            assignment.getString("orderId") == stage.orderId &&
            assignment.getString("orderId") == source.getString("orderId") &&
            assignment.getString("deviceId") == stage.targetDeviceId &&
            assignment.getString("operatorId") == stage.targetUserId &&
            assignment.getString("epoch") == decision.getString("assignmentEpoch") &&
            assignment.getString("startOrderScanSeq") == next &&
            assignment.getString("startOrderScanSeq") == decision.getString("startOrderScanSeq")) {
            "目标授权与签名交接证据不一致"
        }
        return assignment
    }
}
