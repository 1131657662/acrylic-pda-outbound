package com.acrylic.outbound.pda.relay

import com.acrylic.outbound.pda.rescue.RescueBundle
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.TemporarySuccessJson
import org.json.JSONObject
import java.util.Base64
import java.util.UUID

/** File-supplied keys never establish trust; caller supplies the original preparation key. */
object RelayReceiptVerifier {
    private val fields = setOf("purpose", "relayId", "resultId", "exportId", "archiveSha256", "deviceKeySha256",
        "operatorId", "deviceId", "warehouseId", "localOrderId", "submissionId", "payloadSha256", "sourceRecoveryEpoch",
        "relayOperatorId", "localDeletionAllowed", "businessReceipt", "signedRelayReceipt")

    fun verify(bytes: ByteArray, identity: JSONObject, body: String, publicKey: ByteArray, trustedKey: JSONObject): JSONObject {
        val file = TemporarySuccessJson.parse(bytes)
        check(file.keys().asSequence().toSet() == fields) { "中转回执字段无效" }
        val token = file.getString("signedRelayReceipt"); val parts = token.split('.')
        check(parts.size == 3 && token.length <= 262144)
        val claims = TemporarySuccessJson.parse(Base64.getUrlDecoder().decode(parts[1]))
        check(claims.keys().asSequence().toSet() == setOf("iss", "aud", "iat", "receipt"))
        JwtVerifier.verify(token, trustedKey, "acrylic-pda-relay-result", 262144)
        val unsigned = JSONObject(file.toString()).apply { remove("signedRelayReceipt") }
        check(SubmissionJson.canonical(unsigned) == SubmissionJson.canonical(claims.getJSONObject("receipt"))) { "中转文件与签名不符" }
        context(file, identity, body, publicKey)
        return file.getJSONObject("businessReceipt")
    }
    private fun context(file: JSONObject, identity: JSONObject, payload: String, publicKey: ByteArray) {
        check(file.get("purpose") == "PDA_ORIGINAL_RELAY_RESULT_V1" && file.get("localDeletionAllowed") == false)
        for (field in listOf("relayId", "resultId", "exportId", "relayOperatorId")) {
            val value = file.getString(field); check(UUID.fromString(value).toString() == value)
        }
        for (field in listOf("operatorId", "deviceId", "warehouseId", "localOrderId"))
            check(file.getString(field) == identity.getString(field)) { "中转回执不属于原人员/设备/订单" }
        val body = JSONObject(payload)
        check(file.getString("archiveSha256").matches(Regex("[0-9a-f]{64}")))
        check(file.getString("deviceKeySha256") == RescueBundle.sha(publicKey)) { "中转回执不属于原登记设备钥" }
        check(file.getString("submissionId") == body.getString("submissionId") &&
            file.getString("payloadSha256") == JwtVerifier.sha(payload.toByteArray(Charsets.UTF_8)) &&
            file.getString("sourceRecoveryEpoch") == body.getString("sourceRecoveryEpoch")) { "中转回执不属于原冻结正文" }
        check(SubmissionJson.canonical(body) == payload) { "本地原冻结正文损坏" }
    }
    fun requireForward(previousState: String, previous: String?, next: String, receipt: JSONObject) {
        if (previousState in setOf("SYNCED", "ABORTED", "REJECTED")) {
            check(previousState == next && previous != null &&
                SubmissionJson.canonical(JSONObject(previous)) == SubmissionJson.canonical(receipt)) { "原批次已有不同终态，冲突原件保留" }
        }
        if (previousState == "REVIEW_REQUIRED") check(next !in setOf("REGISTERED", "RECEIVING")) { "旧中转回执不能覆盖待审结果" }
    }
}
