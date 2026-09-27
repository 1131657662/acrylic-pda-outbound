package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.sync.SyncResult
import com.acrylic.outbound.pda.sync.SyncRun
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Installs the server's answer to "is one of my TEMP sources now bound to a formal order?". The device only learns
 * this from GET /pda/temporary-source-bindings; without it a linked source stays invisible locally. Installation is
 * gated by the server's own `installRequired` flag (true only for a CONTROLLED_LINK mapping) and is idempotent: the
 * local table is keyed by scopeId and written with REPLACE, so re-reading the same page never duplicates a row.
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

    /** One pass over the binding pages; `more = true` means another pass is worth scheduling. */
    @Synchronized
    fun installBindings(cursor: String = "", run: SyncRun = SyncRun()): SyncResult {
        val auth = JSONObject(currentSession().toString())
        if (auth.optString("deviceId").isEmpty()) return SyncResult("当前设备身份不可用，来源绑定未安装", supported = false)
        val epoch = auth.getString("serverRecoveryEpoch")
        var next = cursor
        var installedTotal = 0
        repeat(run.batches) {
            run.identity(auth, currentSession)
            val data = try {
                request(path(next), epoch, auth)
            } catch (error: ApiFailure) {
                // The read surface shares the temporary command switch: absent means "not opened yet", not a failure.
                if (error.httpStatus == 404) return SyncResult("当前后台尚未提供来源绑定读取，原资料保留", supported = false)
                throw error
            }
            val items = data.optJSONArray("items") ?: JSONArray()
            var installed = 0
            run.commit {
                db.runInTransaction {
                    run.identity(auth, currentSession)
                    for (index in 0 until items.length()) {
                        val row = items.getJSONObject(index)
                        if (!row.optBoolean("installRequired", false)) continue
                        db.sourceBindings().install(
                            LocalSourceBinding(
                                scopeId = row.getString("scopeId"),
                                clientOrderId = row.getString("clientOrderId"),
                                canonicalOrderId = row.getString("canonicalOrderId"),
                                mappingVersion = row.getString("mappingVersion"),
                                mappingOrigin = row.getString("mappingOrigin"),
                                workRound = row.getString("workRound"),
                                phase = row.getString("phase"),
                                orderNo = row.getString("orderNo"),
                                installedAt = System.currentTimeMillis(),
                            ),
                        )
                        installed++
                    }
                }
            }
            installedTotal += installed
            // A JSON null cursor means "no more pages"; optString would hand back the literal "null".
            val rawCursor = data.opt("nextCursor")
            next = if (rawCursor == null || rawCursor == JSONObject.NULL) "" else rawCursor.toString()
            if (next.isEmpty()) {
                return SyncResult(if (installedTotal > 0) "已安装 $installedTotal 条来源绑定" else "来源绑定无变化")
            }
        }
        return SyncResult("来源绑定仍在读取，下次联网刷新将继续", more = true)
    }

    private fun path(cursor: String): String {
        val suffix = if (cursor.isEmpty()) "" else "&cursor=${URLEncoder.encode(cursor, "UTF-8")}"
        return "pda/temporary-source-bindings?limit=50$suffix"
    }
}
