package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Immutable, device-signed collection boundary; it grants no business or deletion authority. */
internal object IndependentClosure {
    const val EVENT = "INDEPENDENT_CLOSED"
    const val PURPOSE = "INDEPENDENT_CAPTURE_CLOSED_V1"
    fun seal(snapshot: RescueSnapshot, time: TimeSample, reason: String, signer: RescueSigner): JSONObject {
        val body = expected(snapshot, time, reason)
        val keySha = RescueBundle.sha(signer.publicKey)
        check(keySha == source(snapshot).getJSONObject("credential").getString("deviceKeySha256")) { "结束签名必须使用原登记设备钥" }
        val text = SubmissionJson.canonical(body)
        val signature = RescueSeal.sign("$PURPOSE\n$text", signer)
        return JSONObject().put("purpose", PURPOSE).put("body", text).put("bodySha256", RescueBundle.sha(text.toByteArray(Charsets.UTF_8)))
            .put("devicePublicKey", Base64.getEncoder().encodeToString(signer.publicKey))
            .put("signature", Base64.getEncoder().encodeToString(signature))
    }
    fun verify(snapshot: RescueSnapshot): JSONObject? {
        val endings = snapshot.events.filter { it.getString("type") == EVENT }
        if (snapshot.order.getString("state") != "SEALED") { check(endings.isEmpty()); return null }
        check(endings.size == 1 && snapshot.events.last() === endings.single()) { "结束必须为完整审计的唯一末事件" }
        val event = endings.single(); val envelope = JSONObject(event.getString("detail"))
        check(envelope.keys().asSequence().toSet() == setOf("purpose", "body", "bodySha256", "devicePublicKey", "signature"))
        check(envelope.getString("purpose") == PURPOSE)
        val text = envelope.getString("body"); val body = JSONObject(text)
        val time = TimeSample(event.getLong("elapsed"), event.getLong("wall"), event.getInt("boot"))
        check(text == SubmissionJson.canonical(expected(snapshot, time, body.getString("shortfallReason")))) { "完整结束原事实已变化" }
        check(RescueBundle.sha(text.toByteArray(Charsets.UTF_8)) == envelope.getString("bodySha256"))
        val publicKey = Base64.getDecoder().decode(envelope.getString("devicePublicKey"))
        check(RescueBundle.sha(publicKey) == source(snapshot).getJSONObject("credential").getString("deviceKeySha256"))
        RescueSeal.sign("$PURPOSE\n$text", RescueSigner(publicKey) { envelope.getString("signature") })
        return body
    }
    private fun source(snapshot: RescueSnapshot) = JSONObject(snapshot.originals.getValue("independent-source.json").toString(Charsets.UTF_8))
    private fun expected(snapshot: RescueSnapshot, time: TimeSample, reason: String): JSONObject {
        val source = source(snapshot); val task = source.getJSONObject("task"); val credential = source.getJSONObject("credential")
        check(snapshot.captures.none { it.getString("state") in setOf("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED") ||
            it.getString("state") in setOf("INVALID", "BUSINESS_REJECTED") && it.isNull("failureAckAt") }) { "先处理全部未决和未核失败输入" }
        require(reason.length <= 500 && Charsets.UTF_8.newEncoder().canEncode(reason) && reason.none { Character.isISOControl(it) && it != '\n' })
        val prefix = snapshot.events.filter { it.getString("type") != EVENT }
        check(prefix.all { time.elapsed >= it.getLong("elapsed") }) { "结束采样不能早于原事件" }
        val facts = RescueFacts.summary(snapshot.copy(events = prefix))
        val shortfall = task.getLong("plannedTotal") - facts.getLong("active")
        check(shortfall >= 0 && (shortfall == 0L || reason.isNotBlank())) { "短装结束须记录原因" }
        val at = TrustedTime.now(source.getJSONObject("timeAnchor"), time)
        check(at >= java.time.Instant.parse(credential.getString("issuedAt")) && at < java.time.Instant.parse(credential.getString("expiresAt")))
        return JSONObject().put("purpose", PURPOSE).put("taskId", task.getString("id"))
            .put("operatorId", task.getString("operatorId")).put("deviceId", task.getString("deviceId")).put("warehouseId", task.getString("warehouseId"))
            .put("credentialId", credential.getString("credentialId")).put("credentialSha256", credential.getString("credentialSha256"))
            .put("skuSha256", task.getString("skuSha256")).put("commandSha256", task.getString("commandSha256"))
            .put("recoveryEpoch", task.getString("recoveryEpoch")).put("inputHighWater", snapshot.captures.size.toString())
            .put("eventHighWater", prefix.size.toString()).put("plannedTotal", task.getString("plannedTotal"))
            .put("active", facts.getString("active")).put("void", facts.getString("void")).put("weightGrams", facts.getString("weightGrams"))
            .put("shortfall", shortfall.toString()).put("shortfallReason", reason).put("closedAt", at.toString())
            .put("closedWall", time.wall.toString()).put("closedElapsed", time.elapsed.toString()).put("closedBoot", time.boot.toString())
            .put("factsSha256", factsSha(snapshot, source, prefix))
            .put("pendingReconciliation", true).put("businessProcessed", false).put("localDeletionAllowed", false)
    }
    private fun factsSha(snapshot: RescueSnapshot, source: JSONObject, prefix: List<JSONObject>): String {
        val original = JSONObject().put("source", source).put("captures", JSONArray(snapshot.captures)).put("scans", JSONArray(snapshot.scans))
            .put("lines", JSONArray(snapshot.lines)).put("events", JSONArray(prefix))
            .put("voids", JSONArray(snapshot.originals.getValue("independent-voids.ndjson").toString(Charsets.UTF_8)
                .lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()))
        return RescueBundle.sha(SubmissionJson.canonical(original).toByteArray(Charsets.UTF_8))
    }
}
