package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

internal object HandoverReceiptVerifier {
    fun verify(receipt: JSONObject, key: JSONObject, audience: String, issuer: String): JSONObject {
        val claims = JwtVerifier.verify(receipt.getString("signedReceipt"), key, audience,
            maximumLength = 4 * 1024 * 1024, issuer = issuer)
        val unsigned = JSONObject(receipt.toString()).apply { remove("signedReceipt") }
        check(SubmissionJson.canonical(unsigned) == SubmissionJson.canonical(claims.getJSONObject("receipt"))) {
            "交接回执正文与签名不一致"
        }
        val flags = unsigned.getJSONObject("capabilities")
        val fields = setOf("writeConfirmationAvailable", "releaseAllowed", "mayResumeScanning", "stockReleased", "localDeletionAllowed")
        check(flags.keys().asSequence().toSet() == fields) { "交接回执包含未知能力" }
        for (field in fields) check(flags.get(field) == false) { "交接回执包含未开放能力" }
        return unsigned
    }
}
