package com.acrylic.outbound.pda.submission

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject

object ReceiptVerifier {
    fun verify(local: LocalSubmission, response: JSONObject, key: JSONObject): String {
        val body = JSONObject(local.payload)
        check(response.getString("submissionId") == local.id && response.getString("orderId") == body.getJSONObject("source").getString("sourceOrderId")) { "云端回执不属于原订单" }
        val status = response.getString("status")
        if (status !in setOf("SUCCEEDED", "ABORTED")) {
            check(status in setOf("REGISTERED", "RECEIVING", "REVIEW_REQUIRED", "REJECTED")) { "未知提交状态" }
            if (!response.isNull("payloadSha256")) check(response.getString("payloadSha256") == local.sha256) { "云端正文摘要不一致" }
            return status
        }
        val signed = JwtVerifier.verify(response.getString("signedReceipt"), key, "acrylic-pda-receipt", 1024 * 1024).getJSONObject("receipt")
        val unsigned = JSONObject(response.toString()).apply { remove("signedReceipt") }
        check(SubmissionJson.canonical(signed) == SubmissionJson.canonical(unsigned)) { "云端回执签名与响应不一致" }
        check(response.getString("freezeDeviceId") == body.getString("freezeDeviceId") && response.getString("workRound") == body.getString("workRound") &&
            response.getString("issuedRecoveryEpoch") == body.getString("sourceRecoveryEpoch") &&
            SubmissionJson.canonical(response.getJSONObject("source")) == SubmissionJson.canonical(body.getJSONObject("source"))) { "云端回执原始来源不一致" }
        if (status == "ABORTED") return status
        check(response.getString("payloadSha256") == local.sha256 && response.getString("actualCount") == body.getString("expectedCount") &&
            response.getString("actualWeightGrams") == body.getString("expectedWeightGrams") && response.getString("captureHighWatermark") == body.getString("captureHighWatermark")) { "云端汇总与冻结记录不一致" }
        products(body, response)
        val active = com.acrylic.outbound.pda.continuation.DispositionRecords.effective(body)
        check(response.getString("firstOrderScanSeq") == active.first().getString("orderScanSeq") && response.getString("lastOrderScanSeq") == active.last().getString("orderScanSeq") &&
            response.getBoolean("inventoryDeducted") == body.getBoolean("autoDeductInventory")) { "回执序号或扣账模式不一致" }
        check(response.getString("orderStatus") == "COMPLETED" && response.getString("resultVersion") == "1") { "云端尚未确认出库" }
        return "SYNCED"
    }
    private fun products(body: JSONObject, response: JSONObject) {
        val expected = sortedMapOf<String, Pair<Long, Long>>()
        for (scan in com.acrylic.outbound.pda.continuation.DispositionRecords.effective(body)) {
            val code = scan.getString("deliveryCode"); val value = expected[code] ?: (0L to 0L)
            expected[code] = value.first + 1 to Math.addExact(value.second, scan.getString("weightGrams").toLong())
        }
        val actual = sortedMapOf<String, Pair<Long, Long>>(); val items = response.getJSONArray("items")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i); val code = item.getString("deliveryCode")
            check(!actual.containsKey(code)) { "回执产品重复" }
            actual[code] = item.getString("actualCount").toLong() to item.getString("actualWeightGrams").toLong()
        }
        check(actual.filterValues { it.first != 0L || it.second != 0L } == expected) { "分产品回执与本地明细不符" }
    }
}
