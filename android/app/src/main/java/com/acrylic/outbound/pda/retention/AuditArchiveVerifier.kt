package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.*
import java.time.*

object AuditArchiveVerifier {
    fun payload(device: String, after: Long, through: Long, events: JSONArray): JSONObject {
        check(after >= 0 && through > after && through - after <= 200 && events.length().toLong() == through - after) { "审计归档范围或连续性错误" }
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            check(event.getString("originDeviceId") == device && event.getString("localSeq") == (after + i + 1).toString()) { "审计归档存在缺口或其他设备记录" }
        }
        return JSONObject().put("originDeviceId", device).put("afterSeq", after.toString()).put("throughSeq", through.toString()).put("events", events)
    }
    fun digest(body: JSONObject) = JwtVerifier.sha(SubmissionJson.canonical(body).toByteArray(Charsets.UTF_8))
    fun verify(row: AuditArchive, proof: JSONObject, key: JSONObject, warehouse: String) {
        val claims = JwtVerifier.verify(proof.getString("signedArchive"), key, "acrylic-pda-audit-archive").getJSONObject("archive")
        val unsigned = JSONObject(proof.toString()).apply { remove("signedArchive") }
        check(SubmissionJson.canonical(claims) == SubmissionJson.canonical(unsigned)) { "审计归档签名与响应不一致" }
        check(row.afterSeq >= 0 && row.throughSeq > row.afterSeq && row.throughSeq - row.afterSeq <= 200) { "审计归档段范围无效" }
        val expected = JSONObject().put("archiveVersion", 1).put("originDeviceId", row.originDeviceId).put("warehouseId", warehouse)
            .put("recoveryEpoch", row.recoveryEpoch).put("afterSeq", row.afterSeq.toString()).put("throughSeq", row.throughSeq.toString())
            .put("payloadSha256", row.payloadSha256).put("storagePolicy", "SERVER_DURABLE_FILES_V1").put("localDeletionAllowed", false)
        expected.keys().forEach { check(expected.get(it) == proof.get(it)) { "审计归档来源或摘要不一致：$it" } }
        val at = Instant.parse(proof.getString("archivedAt"))
        check(proof.getString("byteCount").toLong() in 1..512*1024 &&
            !Instant.parse(proof.getString("retainUntil")).isBefore(at.atOffset(ZoneOffset.UTC).plusYears(5).toInstant())) { "审计归档长度或留存期限错误" }
    }
    fun prefix(rows: List<AuditArchive>, warehouse: String): List<AuditArchive> {
        check(rows.size <= 1024) { "审计归档超过当前恢复资源限额，请保留数据联系管理员" }
        var next = 0L
        var pending = false
        val completed = ArrayList<AuditArchive>()
        rows.forEach { row ->
            check(row.originDeviceId == rows.first().originDeviceId && row.recoveryEpoch == rows.first().recoveryEpoch) { "审计归档命名空间不一致" }
            if (row.proofJson == null) {
                check(!pending && row.afterSeq == next) { "审计归档待确认段不连续" }
                pending = true
                check(row.signingKeyJson == null) { "未完成审计归档不能带签名" }
            } else {
                check(!pending) { "审计归档完成记录不能越过未完成请求" }
                check(row.afterSeq == next) { "审计归档前缀存在缺口" }
                verify(row, JSONObject(row.proofJson), JSONObject(checkNotNull(row.signingKeyJson)), warehouse)
                completed += row
                next = row.throughSeq
            }
        }
        return completed
    }
}
