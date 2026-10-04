package com.acrylic.outbound.pda.continuation

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Verifies source-device audit links before historical rows can be imported. */
internal object HandoverRecoveryAudit {
    fun verify(stage: LocalHandoverStage) {
        val body = JSONObject(stage.recoveryBytes.toString(Charsets.UTF_8))
        val source = JSONObject(stage.manifestJson).getJSONObject("stopReceipt").getJSONObject("source")
        val captures = captures(body.getJSONArray("captures"), stage, source)
        val events = events(body.getJSONArray("events"), captures, source)
        scans(body.getJSONArray("scans"), captures, events.first, events.second)
        for ((id, capture) in captures) terminal(capture, events.first[id].orEmpty())
    }

    private fun captures(rows: JSONArray, stage: LocalHandoverStage, source: JSONObject): Map<String, JSONObject> {
        val result = HashMap<String, JSONObject>()
        var previous = 0L
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val id = UUID.fromString(row.getString("id")).toString()
            val sequence = row.getString("deviceCaptureSeq").toLong()
            check(result.put(id, row) == null && sequence > previous) { "原设备输入序号重复或乱序" }
            previous = sequence
            check(row.getString("originDeviceId") == source.getString("deviceId") &&
                row.getString("operatorId") == source.getString("operatorId") &&
                row.getString("warehouseId") == stage.warehouseId &&
                row.getString("orderId") == stage.orderId) { "原输入来源与停止证明不一致" }
            UUID.fromString(row.getString("assignmentId"))
        }
        return result
    }

    private fun events(rows: JSONArray, captures: Map<String, JSONObject>, source: JSONObject):
        Pair<Map<String, Set<String>>, Map<Long, JSONObject>> {
        val types = HashMap<String, MutableSet<String>>()
        val sequences = HashMap<Long, JSONObject>()
        val ids = HashSet<String>()
        var previous = 0L
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val sequence = row.getString("localSeq").toLong()
            check(ids.add(UUID.fromString(row.getString("id")).toString()) && sequence > previous) {
                "原审计ID或设备序号重复乱序"
            }
            previous = sequence
            check(row.getString("originDeviceId") == source.getString("deviceId") &&
                row.getString("operatorId") == source.getString("operatorId")) { "原审计来源与停止证明不一致" }
            UUID.fromString(row.getString("assignmentId"))
            if (!row.isNull("captureId")) {
                val id = UUID.fromString(row.getString("captureId")).toString()
                val capture = checkNotNull(captures[id]) { "原审计指向不存在输入" }
                check(row.getString("assignmentId") == capture.getString("assignmentId")) {
                    "原审计与输入授权不一致"
                }
                types.getOrPut(id) { HashSet() }.add(row.getString("type"))
            }
            sequences[sequence] = row
        }
        return types to sequences
    }

    private fun scans(rows: JSONArray, captures: Map<String, JSONObject>, types: Map<String, Set<String>>,
        events: Map<Long, JSONObject>) {
        for (index in 0 until rows.length()) {
            val scan = rows.getJSONObject(index)
            val id = UUID.fromString(scan.getString("captureId")).toString()
            val capture = checkNotNull(captures[id]) { "原保存记录缺少输入" }
            val saved = events[scan.getString("localSeq").toLong()]
            check(saved != null && saved.getString("type") == "SAVED" && saved.getString("captureId") == id &&
                saved.getString("assignmentId") == capture.getString("assignmentId")) {
                "原保存记录缺少对应审计"
            }
            if (scan.getString("status") == "VOID") check("VOID" in types[id].orEmpty()) { "原撤销记录缺少审计" }
        }
    }

    private fun terminal(capture: JSONObject, audit: Set<String>) {
        val state = capture.getString("state")
        check("RECEIVED" in audit && (if (state == "COMMITTED") "SAVED" else state) in audit) {
            "原输入缺少接收或终态审计"
        }
        if (state == "COMMITTED") check("CONFIRM_REQUESTED" in audit) { "原输入缺少确认意图审计" }
        if (state == "INVALID" || state == "BUSINESS_REJECTED") {
            check("FAILURE_ACKNOWLEDGED" in audit) { "原失败输入缺少处理审计" }
        }
    }
}
