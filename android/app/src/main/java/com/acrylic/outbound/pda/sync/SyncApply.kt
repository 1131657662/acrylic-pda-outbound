package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.data.*
import org.json.JSONObject
import java.time.Instant

/** Persist a response and its checkpoint together, after verifying the request still owns the checkpoint. */
class SyncApply(private val db: PdaDatabase) {
    fun apply(expected: LocalSyncState?, namespace: LocalSyncState, data: JSONObject) {
        check(data.getString("streamType") == "ORDER" && data.getString("recoveryEpoch") == namespace.serverRecoveryEpoch &&
            data.getString("warehouseId") == namespace.warehouseId) { "同步响应不属于当前仓库或恢复代次" }
        db.runInTransaction {
            check(db.sync().state(namespace.serverRecoveryEpoch, namespace.warehouseId) == expected) { "同步进度已变化，请重新刷新" }
            if (text(data, "baselineId") != null) baseline(expected ?: namespace, data) else delta(checkNotNull(expected), data)
        }
    }
    private fun baseline(state: LocalSyncState, data: JSONObject) {
        val id = data.getString("baselineId"); val high = number(data, "highWatermark")
        check(state.downloadId == null || state.downloadId == id) { "基线分页标识发生变化" }
        check(state.downloadWatermark == null || state.downloadWatermark == high) { "基线高水位发生变化" }
        check(state.cursor == null && data.getJSONArray("events").length() == 0) { "基线响应与当前请求不一致" }
        val complete = data.getBoolean("baselineComplete"); val next = text(data, "baselineNextCursor")
        check(complete == (next == null) && (complete || next != state.downloadCursor)) { "基线分页未推进" }
        val cursor = text(data, "nextCursor")
        check(complete == (cursor != null)) { "未完整下载基线，不能启用增量游标" }
        val rows = data.getJSONArray("baseline")
        for (index in 0 until rows.length()) {
            val row = row(state, id, rows.getJSONObject(index), "rowVersion", "updatedAt")
            check(db.sync().order(state.serverRecoveryEpoch, state.warehouseId, id, row.orderId) == null) { "基线包含重复订单" }
            db.sync().saveOrder(row)
        }
        db.sync().saveState(if (complete) state.copy(cursor = cursor, sequence = high, activeBaselineId = id,
            downloadId = null, downloadCursor = null, downloadWatermark = null, lastSuccessAt = Instant.now().toString())
        else state.copy(downloadId = id, downloadCursor = next, downloadWatermark = high))
    }
    private fun delta(state: LocalSyncState, data: JSONObject) {
        check(state.cursor != null && state.downloadId == null && data.getBoolean("baselineComplete") &&
            text(data, "baselineNextCursor") == null && data.getJSONArray("baseline").length() == 0) { "增量响应与当前请求不一致" }
        val high = number(data, "highWatermark"); check(high >= state.sequence) { "云端进度发生倒退，请保留资料核对" }
        val baseline = checkNotNull(state.activeBaselineId); var sequence = state.sequence
        val events = data.getJSONArray("events")
        for (index in 0 until events.length()) {
            val event = events.getJSONObject(index); val next = number(event, "sequence")
            check(next == Math.addExact(sequence, 1) && next <= high) { "同步事件不连续，请重试原进度" }
            val row = row(state, baseline, event, "entityVersion", "createdAt")
            val old = db.sync().order(state.serverRecoveryEpoch, state.warehouseId, baseline, row.orderId)
            if (old == null || row.rowVersion > old.rowVersion) db.sync().saveOrder(row)
            else if (row.rowVersion == old.rowVersion) check(row == old) { "同版本订单状态不一致" }
            sequence = next
        }
        check(data.getBoolean("hasMore") || sequence == high) { "同步分页遗漏事件" }
        val cursor = checkNotNull(text(data, "nextCursor"))
        check((sequence == state.sequence) == (cursor == state.cursor)) { "同步游标与事件进度不一致" }
        db.sync().saveState(state.copy(cursor = cursor, sequence = sequence, lastSuccessAt = Instant.now().toString()))
    }
    private fun row(state: LocalSyncState, baseline: String, data: JSONObject, versionField: String, timeField: String): LocalCloudOrder {
        val version = number(data, versionField); check(version > 0)
        val status = data.getString("status")
        check(status in setOf("READY", "ASSIGNED", "RECALL_PENDING", "REVIEW_REQUIRED", "COMPLETED", "CANCELLED", "CONTINUING", "LINKED"))
        val id = java.util.UUID.fromString(data.getString("orderId")).toString()
        val time = Instant.parse(data.getString(timeField)).toString()
        return LocalCloudOrder(state.serverRecoveryEpoch, state.warehouseId, baseline, id, status, version, time)
    }
    private fun number(data: JSONObject, name: String): Long {
        val raw = data.get(name); check(raw is String && raw.matches(Regex("0|[1-9][0-9]*"))) { "同步数值格式无效" }
        return raw.toLong()
    }
    private fun text(data: JSONObject, name: String): String? =
        if (data.isNull(name)) null else data.getString(name).also { check(it.isNotBlank() && it != "null") }
}
