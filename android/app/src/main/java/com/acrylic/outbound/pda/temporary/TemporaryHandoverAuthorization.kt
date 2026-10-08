package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.continuation.HandoverReceiptVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.time.Instant

/** Only a signed TEMP handover activation can shorten the ordinary authorization duration. */
internal object TemporaryHandoverAuthorization {
    fun declaration(request: TemporaryRequest): JSONObject {
        val receipt = JSONObject(checkNotNull(request.receiptJson))
        val key = JSONObject(request.signingKeyJson)
        val activation = JSONObject(request.bodyJson).optJSONObject("temporaryHandoverActivation")
        val issued = activation?.let { verify(it, receipt, key) }
        return TemporaryVerifier.manifest(receipt, key, issued)
    }

    fun verify(activation: JSONObject, authorization: JSONObject, key: JSONObject): Instant {
        val signed = HandoverReceiptVerifier.verify(activation, key,
            "acrylic-outbound-handover-receipt", "acrylic-outbound-server")
        check(signed.getString("commandType") == "ACTIVATE" && signed.getString("status") == "RECORDED")
        val decision = signed.getJSONObject("businessDecision")
        check(decision.getString("kind") == "TEMP_HANDOVER_ACTIVATE" && decision.getString("status") == "ACTIVATED")
        check(SubmissionJson.canonical(decision.getJSONObject("authorization")) == SubmissionJson.canonical(authorization))
        check(decision.getString("newAuthorizationId") == authorization.getString("authorizationId"))
        return Instant.parse(signed.getString("recordedAt")).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
    }
}
