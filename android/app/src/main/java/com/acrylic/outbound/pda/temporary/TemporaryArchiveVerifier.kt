package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

object TemporaryArchiveVerifier {
    fun verify(request: TemporaryArchiveRequest, original: ByteArray, key: JSONObject): JSONObject {
        val envelope = TemporarySuccessJson.parse(original); check(envelope.get("code") == "OK")
        val proof = envelope.getJSONObject("data"); signature(proof, key)
        val expected = TemporarySuccessJson.parse(request.contextJson.toByteArray(Charsets.UTF_8))
        val extra = setOf("commandReceiptId", "commandResponseSha256", "commandBytes", "archivedAt", "retainUntil", "signedArchive")
        check(proof.keys().asSequence().toSet() == expected.keys().asSequence().toSet() + extra)
        expected.keys().forEach { field ->
            check(SubmissionJson.canonical(proof.get(field)) == SubmissionJson.canonical(expected.get(field))) {
                "归档证明与原冻结来源不一致：$field"
            }
        }
        TemporaryVerifier.uuid(proof.get("commandReceiptId") as String)
        check((proof.get("commandResponseSha256") as String).matches(Regex("[a-f0-9]{64}")))
        val count = proof.get("commandBytes") as String
        check(count.matches(Regex("[1-9][0-9]*")) && count.toLong() <= 1048576)
        val archived = Instant.parse(proof.get("archivedAt") as String)
        check(!archived.isBefore(Instant.parse(proof.get("completedAt") as String)))
        check(!Instant.parse(proof.get("retainUntil") as String).isBefore(archived.atOffset(ZoneOffset.UTC).plusYears(5).toInstant()))
        return proof
    }

    private fun signature(proof: JSONObject, key: JSONObject) {
        val token = proof.get("signedArchive") as String; val parts = token.split('.')
        check(parts.size == 3 && token.length <= 262144)
        val decoded = parts.map { part ->
            check(part.matches(Regex("[A-Za-z0-9_-]+")))
            Base64.getUrlDecoder().decode(part).also { check(Base64.getUrlEncoder().withoutPadding().encodeToString(it) == part) }
        }
        val header = TemporarySuccessJson.parse(decoded[0])
        check(header.keys().asSequence().toSet() == setOf("alg", "typ", "kid"))
        val claims = TemporarySuccessJson.parse(decoded[1])
        check(claims.keys().asSequence().toSet() == setOf("iss", "aud", "archive"))
        JwtVerifier.verify(token, key, "acrylic-pda-archive", 262144)
        val unsigned = JSONObject(proof.toString()).apply { remove("signedArchive") }
        check(SubmissionJson.canonical(claims.getJSONObject("archive")) == SubmissionJson.canonical(unsigned)) { "归档签名与原响应不一致" }
    }

    fun digest(value: JSONObject): String = JwtVerifier.sha(SubmissionJson.canonical(value).toByteArray(Charsets.UTF_8))
}
