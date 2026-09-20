package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.network.*
import java.net.URLEncoder

class BarcodeRepository internal constructor(private val db: PdaDatabase,
    private val currentSession: () -> org.json.JSONObject, private val request: (String, String, org.json.JSONObject) -> org.json.JSONObject) {
    internal constructor(db: PdaDatabase, current: () -> org.json.JSONObject, request: (String, String) -> org.json.JSONObject)
        : this(db, current, { path, epoch, _ -> request(path, epoch) })
    constructor(db: PdaDatabase, session: PdaSession) : this(db, session::requireSession,
        { path, epoch, auth -> session.call(path, epoch = epoch, expected = auth) })
    /** Bound each foreground refresh; an unfinished baseline remains staged and resumes next time. */
    fun refreshBarcodes(limit: Int = 50): String = syncBarcodes(limit).message
    @Synchronized fun syncBarcodes(limit: Int = 50, run: SyncRun = SyncRun()): SyncResult {
        require(limit in 1..200)
        val auth = org.json.JSONObject(currentSession().toString())
        val namespace = LocalSyncState(auth.getString("serverRecoveryEpoch"), auth.getString("warehouseId"), "BARCODE")
        repeat(run.batches) {
            run.identity(auth, currentSession)
            val previous = db.sync().state(namespace.serverRecoveryEpoch, namespace.warehouseId, "BARCODE")
            val data = try { request(path(previous, limit), namespace.serverRecoveryEpoch, auth) }
            catch (error: ApiFailure) {
                if (error.httpStatus == 404) return SyncResult("当前测试后台尚未提供条码同步，仍使用联网查询与原作业授权", supported = false)
                if (error.code != "SYNC_BASELINE_EXPIRED") throw error
                run.commit { db.runInTransaction { run.identity(auth, currentSession); discardExpired(previous ?: throw error) } }
                return@repeat
            }
            run.commit { db.runInTransaction { run.identity(auth, currentSession); BarcodeApply(db).apply(previous, namespace, data) } }
            if (data.getBoolean("baselineComplete") && !data.getBoolean("hasMore")) return SyncResult("条码状态已同步")
        }
        return SyncResult("条码状态仍在同步，下次联网刷新将继续；原作业资料已保留", more = true)
    }
    private fun path(state: LocalSyncState?, limit: Int): String {
        val suffix = when {
            state?.downloadId != null -> "&baselineId=${encode(state.downloadId)}&baselineCursor=${encode(state.downloadCursor)}"
            state?.cursor != null -> "&cursor=${encode(state.cursor)}"
            else -> ""
        }
        return "pda/sync/barcodes?limit=$limit$suffix"
    }
    private fun discardExpired(state: LocalSyncState) {
        db.runInTransaction {
            check(db.sync().state(state.serverRecoveryEpoch, state.warehouseId, "BARCODE") == state) { "同步进度已变化，请重新刷新" }
            state.downloadId?.let { db.barcodes().discardStaging(state.serverRecoveryEpoch, state.warehouseId, it) }
            db.sync().saveState(state.copy(cursor = null, downloadId = null, downloadCursor = null, downloadWatermark = null))
        }
    }
    private fun encode(value: String?): String = value?.let { URLEncoder.encode(it, "UTF-8") }.orEmpty()
}
