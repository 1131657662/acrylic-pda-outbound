package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.sync.SyncResult
import com.acrylic.outbound.pda.sync.SyncRun
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Installs the server's answer to "is one of my TEMP sources now bound to a formal order?". The device only learns
 * this from GET /pda/temporary-source-bindings; without it a linked source stays invisible locally. Installation is
 * gated by a verified CONTROLLED_LINK projection. A page commits atomically after checking source continuity,
 * monotonic mapping/round versions and the requesting session; the same projection makes no local change.
 */
class SourceBindingRepository internal constructor(
    private val db: PdaDatabase,
    private val currentSession: () -> JSONObject,
    private val request: (String, String, JSONObject) -> JSONObject,
) {
    internal constructor(db: PdaDatabase, current: () -> JSONObject, request: (String, String) -> JSONObject)
        : this(db, current, { path, epoch, _ -> request(path, epoch) })

    constructor(db: PdaDatabase, session: PdaSession) : this(db, session::requireSession,
        { path, epoch, auth -> session.call(path, epoch = epoch, expected = auth) })

    @Synchronized
    fun installBindings(cursor: String = "", run: SyncRun = SyncRun()): SyncResult = sync(cursor, run, null)

    /** A result consumer must not depend on reaching its source in the bounded background scan. */
    @Synchronized
    internal fun installSource(clientOrderId: String): SyncResult = sync("", SyncRun(batches = 1), TemporaryVerifier.uuid(clientOrderId))

    private fun sync(cursor: String, run: SyncRun, clientOrderId: String?): SyncResult {
        val auth = JSONObject(currentSession().toString())
        if (auth.optString("deviceId").isEmpty()) return SyncResult("当前设备身份不可用，来源绑定未安装", supported = false)
        val checkpoint = if (cursor.isEmpty() && clientOrderId == null) SourceBindingCheckpoint(db, auth) else null
        var next = cursor; var installed = 0
        repeat(run.batches) {
            identity(auth, run)
            val previous = checkpoint?.read()
            if (checkpoint != null) next = previous?.cursor.orEmpty()
            val data = read(path(next, clientOrderId), auth) ?: return SyncResult("当前后台尚未提供来源绑定读取，原资料保留", supported = false)
            val after = nextCursor(data, next)
            if (clientOrderId != null) requireSource(data, clientOrderId)
            run.commit { db.runInTransaction {
                identity(auth, run)
                installed += SourceBindingPage(db).install(data)
                checkpoint?.save(previous, after)
                identity(auth, run)
            } }
            next = after
            if (next.isEmpty()) return SyncResult(if (installed > 0) "已安装 $installed 条来源绑定" else "来源绑定无变化")
        }
        return SyncResult("来源绑定仍在读取，下次联网刷新将继续", more = true)
    }

    private fun read(path: String, auth: JSONObject): JSONObject? = try {
        request(path, auth.getString("serverRecoveryEpoch"), auth)
    } catch (error: ApiFailure) {
        if (error.httpStatus != 404) throw error
        null
    }

    private fun identity(auth: JSONObject, run: SyncRun) {
        run.identity(auth, currentSession)
        val latest = currentSession()
        for (key in listOf("userAuthVersion", "deviceVersion")) check(auth.optString(key) == latest.optString(key)) {
            "人员或设备授权版本已变化，来源绑定未安装"
        }
    }

    private fun nextCursor(data: JSONObject, before: String): String {
        val value = data.get("nextCursor")
        if (value == JSONObject.NULL) return ""
        check(value is String) { "来源绑定游标无效" }
        TemporaryVerifier.uuid(value)
        check(before.isEmpty() || value < before) { "来源绑定游标未前进" }
        return value
    }

    private fun requireSource(data: JSONObject, client: String) {
        val items = data.getJSONArray("items")
        check(data.isNull("nextCursor") && items.length() <= 1) { "后台尚未提供原来源精确查询，原资料保留" }
        if (items.length() == 1) check(items.getJSONObject(0).getString("clientOrderId") == client) { "来源绑定查询返回了其他来源" }
    }

    private fun path(cursor: String, client: String?): String {
        val after = if (cursor.isEmpty()) "" else "&cursor=${URLEncoder.encode(cursor, "UTF-8")}"
        val source = if (client == null) "" else "&clientOrderId=$client"
        return "pda/temporary-source-bindings?limit=50$after$source"
    }
}
