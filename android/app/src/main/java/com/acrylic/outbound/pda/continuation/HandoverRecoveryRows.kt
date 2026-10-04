package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.submission.SubmissionRecords
import com.acrylic.outbound.pda.work.*
import org.json.JSONArray
import org.json.JSONObject

/** Reparents local rows while preserving every serialized source fact. */
internal class HandoverRecoveryRows(body: JSONObject, target: String) {
    val captures = rows(body.getJSONArray("captures")) { capture(it, target) }
    val scans = rows(body.getJSONArray("scans")) { scan(it, target) }
    val events = rows(body.getJSONArray("events")) { event(it, target) }
    val dispositions = rows(body.getJSONArray("dispositions")) { it }.mapIndexed { index, proof ->
        LocalDisposition(proof.getString("captureId"), target, proof.getString("approvedEvidenceId"),
            proof.getString("action"), SubmissionJson.canonical(proof), index)
    }

    init {
        equal(body, "captures", captures.map(SubmissionRecords::capture))
        equal(body, "scans", scans.map(SubmissionRecords::scan))
        equal(body, "events", events.map(SubmissionRecords::event))
        check(captures.all { it.state != "COMMITTED" || it.confirmedAt != null }) { "原保存输入缺少确认时间" }
        check(captures.all { it.state !in setOf("INVALID", "BUSINESS_REJECTED") || it.failureAckAt != null }) {
            "原失败输入缺少处理时间"
        }
        check(scans.all { it.weightGrams > 0 && it.expectedDispatchCycle > 0 }) { "原保存重量或周期无效" }
    }

    private fun capture(row: JSONObject, target: String): WorkCapture {
        val context = CaptureContext(target, row.getString("orderId"), row.getString("originDeviceId"),
            row.getString("operatorId"), row.getString("warehouseId"), number(row, "orderScanSeq"),
            number(row, "deviceCaptureSeq"), row.getString("rawInput"), row.getString("barcode"),
            row.getString("inputSource"), row.getString("confirmationPolicy"), number(row, "receivedWallMs"),
            number(row, "receivedElapsedMs"), row.getInt("receivedBoot"), row.getString("snapshotId"),
            text(row, "expectedDispatchCycle")?.toLong(), row.getString("sourceRecoveryEpoch"),
            row.getString("authorizationId"), row.getString("assignmentEpoch"), row.getInt("orderRevision"),
            row.getInt("inputRevision"), row.getString("assignmentId"))
        return WorkCapture(row.getString("id"), context, row.getString("state"), row.getString("error"),
            text(row, "confirmedAt"), text(row, "failureAckAt"))
    }

    private fun scan(row: JSONObject, target: String) = WorkScan(row.getString("captureId"), target,
        number(row, "orderScanSeq"), row.getString("barcode"), row.getString("baleId"),
        row.getString("deliveryCode"), row.getString("model"), row.getString("name"),
        row.getString("rawWeight"), number(row, "weightGrams"), number(row, "expectedDispatchCycle"),
        row.getString("savedAt"), number(row, "localSeq"), row.getString("status"))

    private fun event(row: JSONObject, target: String) = WorkEvent(row.getString("id"), target,
        text(row, "captureId"), row.getString("originDeviceId"), row.getString("operatorId"),
        number(row, "localSeq"), row.getString("type"), row.getString("detail"), number(row, "wallMs"),
        number(row, "elapsedMs"), row.getInt("boot"), row.getString("assignmentId"))

    private fun <T> rows(array: JSONArray, decode: (JSONObject) -> T): List<T> =
        (0 until array.length()).map { decode(array.getJSONObject(it)) }

    private fun number(row: JSONObject, key: String) = row.getString(key).toLong()
    private fun text(row: JSONObject, key: String): String? = if (row.isNull(key)) null else row.getString(key)
    private fun equal(body: JSONObject, key: String, rows: List<JSONObject>) {
        check(SubmissionJson.canonical(body.getJSONArray(key)) == SubmissionJson.canonical(JSONArray(rows))) {
            "原恢复事实无法无损导入：$key"
        }
    }
}
