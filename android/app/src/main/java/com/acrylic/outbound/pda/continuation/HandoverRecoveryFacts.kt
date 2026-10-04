package com.acrylic.outbound.pda.continuation

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Checks the stopped source facts before the target credential can be used for import. */
internal object HandoverRecoveryFacts {
    fun verify(stage: LocalHandoverStage) {
        val body = JSONObject(stage.recoveryBytes.toString(Charsets.UTF_8))
        check(body.getInt("schemaVersion") == 2 && body.getString("assignmentId") == stage.originalAssignmentId &&
            body.getString("orderId") == stage.orderId &&
            body.getString("captureHighWatermark").toLong() == stage.inputHighWatermark) {
            "原恢复事实与交接来源不一致"
        }
        val captures = body.getJSONArray("captures")
        val scans = body.getJSONArray("scans")
        val events = body.getJSONArray("events")
        check(stage.inputHighWatermark >= 0 && captures.length().toLong() == stage.inputHighWatermark) {
            "原输入高水位与恢复事实不一致"
        }
        val committed = captures(captures)
        val saved = scans(scans, committed)
        summaries(body, captures, scans, saved)
        stop(events, body.getString("recoveryPackageId"), stage.originalAssignmentId)
    }

    private fun captures(rows: JSONArray): Map<String, Long> {
        val committed = HashMap<String, Long>()
        val ids = HashSet<String>()
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val id = UUID.fromString(row.getString("id")).toString()
            check(ids.add(id) && row.getString("orderScanSeq").toLong() == index + 1L) {
                "原输入重复或序号不连续"
            }
            val state = row.getString("state")
            check(state in setOf("COMMITTED", "INVALID", "BUSINESS_REJECTED", "DISCARDED")) {
                "原输入仍有未决状态"
            }
            if (state == "COMMITTED") committed[id] = index + 1L
        }
        return committed
    }

    private fun scans(rows: JSONArray, committed: Map<String, Long>): List<JSONObject> {
        val seen = HashSet<String>()
        val saved = ArrayList<JSONObject>()
        var previous = 0L
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val id = UUID.fromString(row.getString("captureId")).toString()
            val sequence = row.getString("orderScanSeq").toLong()
            check(seen.add(id) && committed[id] == sequence && sequence > previous &&
                row.getString("status") in setOf("ACTIVE", "VOID")) { "原保存记录与输入不一致" }
            previous = sequence
            saved.add(row)
        }
        check(seen.size == committed.size) { "原已确认输入缺少保存记录" }
        return saved
    }

    private fun summaries(body: JSONObject, captures: JSONArray, scans: JSONArray, saved: List<JSONObject>) {
        val excluded = HashSet<String>()
        val active = saved.filter { it.getString("status") == "ACTIVE" }
        for (index in 0 until body.getJSONArray("dispositions").length()) {
            val proof = body.getJSONArray("dispositions").getJSONObject(index)
            val id = proof.getString("captureId")
            check(excluded.add(id) && active.any { it.getString("captureId") == id } &&
                proof.getString("action") in setOf("RELEASE", "QUARANTINE")) { "原处置凭据与保存记录不一致" }
        }
        val effective = active.filter { it.getString("captureId") !in excluded }
        val summary = body.getJSONObject("captureSummary")
        check(summary.getString("received").toLong() == captures.length().toLong() &&
            summary.getString("saved").toLong() == effective.size.toLong() &&
            summary.getString("voided").toLong() == scans.length() - active.size.toLong() &&
            summary.getString("pending") == "0" &&
            body.getString("expectedCount").toLong() == effective.size.toLong() &&
            body.getString("expectedWeightGrams").toLong() == effective.sumOf { it.getString("weightGrams").toLong() }) {
            "原恢复统计与保存记录不一致"
        }
    }

    private fun stop(events: JSONArray, packageId: String, assignmentId: String) {
        val last = events.getJSONObject(events.length() - 1)
        check(last.getString("type") == "RECALL_STOPPED" && last.getString("detail") == packageId &&
            last.getString("assignmentId") == assignmentId && last.isNull("captureId")) {
            "原恢复包缺少停止审计"
        }
    }
}
