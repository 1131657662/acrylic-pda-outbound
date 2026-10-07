package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.LocalSyncState
import com.acrylic.outbound.pda.data.PdaDatabase
import org.json.JSONObject

/** Uses the existing per-stream checkpoint table; projections and progress must commit together. */
internal class SourceBindingCheckpoint(private val db: PdaDatabase, auth: JSONObject) {
    private val namespace = LocalSyncState(auth.getString("serverRecoveryEpoch"), auth.getString("warehouseId"),
        "TEMP_BINDINGS:" + listOf("userId", "deviceId", "userAuthVersion", "deviceVersion").joinToString(":") { auth.optString(it) })
    fun read(): LocalSyncState? = db.sync().state(namespace.serverRecoveryEpoch, namespace.warehouseId, namespace.streamType)
    fun save(previous: LocalSyncState?, cursor: String) {
        check(read() == previous) { "来源绑定同步进度已变化，请重新刷新" }
        db.sync().saveState(namespace.copy(cursor = cursor.ifEmpty { null }, sequence = Math.addExact(previous?.sequence ?: 0, 1)))
    }
}
