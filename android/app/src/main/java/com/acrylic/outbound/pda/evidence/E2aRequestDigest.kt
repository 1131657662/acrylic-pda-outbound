package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.security.MessageDigest

/** Binds locally durable keys to their exact request bodies without changing the wire manifest. */
internal class E2aRequestDigest(stopKey: String, verifyKey: String) {
    private val digest = MessageDigest.getInstance("SHA-256")
    init { digest.update("ACRYLIC-R06/E2A/LOCAL-REQUESTS/1\n$stopKey\n$verifyKey\n".toByteArray(Charsets.UTF_8)) }

    fun append(row: LocalE2aRequest) {
        val entry = JSONObject().put("operation", row.operation).put("ordinal", row.ordinal)
            .put("originalKey", row.originalKey).put("requestSha256", row.requestSha256)
        digest.update((SubmissionJson.canonical(entry) + "\n").toByteArray(Charsets.UTF_8))
    }

    fun finish(): String = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
