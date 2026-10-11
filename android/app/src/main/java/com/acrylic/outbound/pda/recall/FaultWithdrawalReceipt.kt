package com.acrylic.outbound.pda.recall

import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

/** A server withdrawal proves revocation; it never certifies a STOP from this device. */
object FaultWithdrawalReceipt {
    fun verify(request: JSONObject, order: PreparedOrder, authorityId: String): JSONObject {
        val receipt = request.getJSONObject("resolutionProof")
        val signed = JwtVerifier.verify(receipt.getString("signedReceipt"), JSONObject(order.signingKeyJson),
            "acrylic-pda-fault-withdrawal", 1024 * 1024).getJSONObject("receipt")
        val unsigned = JSONObject(receipt.toString()).apply { remove("signedReceipt") }
        check(SubmissionJson.canonical(signed) == SubmissionJson.canonical(unsigned)) { "故障撤回签名与回执不一致" }
        check(receipt.getString("status") == "WITHDRAWN" && receipt.getString("orderStatus") == "DRAFT" &&
            !receipt.getBoolean("mayResumeScanning") && !receipt.getBoolean("originalDeviceStopReceived")) { "故障撤回能力边界不符" }
        check(receipt.getString("changeRequestId") == request.getString("changeRequestId") &&
            receipt.getString("assignmentId") == authorityId && receipt.getString("orderId") == order.orderId &&
            receipt.getInt("revision") == JSONObject(order.assignmentJson).getInt("revision") &&
            receipt.getString("issuedRecoveryEpoch") == JSONObject(order.assignmentJson).getString("serverRecoveryEpoch")) { "故障撤回不属于原领取" }
        return receipt
    }
}
