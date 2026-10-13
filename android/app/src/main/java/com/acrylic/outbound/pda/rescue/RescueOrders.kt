package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.data.PdaDatabase
import java.util.concurrent.Callable
import org.json.JSONObject

data class RescueOrderChoice(val target: RescueTarget, val label: String)
data class RescueOrderPage(val rows: List<RescueOrderChoice>, val nextCursor: String?)
class RescueOrders(private val db: PdaDatabase) {
    fun page(grant: RescueReadGrant, after: String = ""): RescueOrderPage {
        grant.recheck()
        val identity = grant.identity
        return db.runInTransaction(Callable {
            val candidates = candidates(identity, after)
            val permitted = candidates.take(20).filter { candidate ->
                runCatching { grant.check(scope(candidate.target)) }.isSuccess
            }
            grant.recheck()
            RescueOrderPage(permitted, if (candidates.size > 20) candidates[19].target.let { "${it.source}:${it.localOrderId}" } else null)
        })
    }
    private fun candidates(identity: RescueReadIdentity, after: String): List<RescueOrderChoice> =
        db.openHelper.readableDatabase.query("""SELECT source,localOrderId,vehicleNo FROM (
                SELECT 'FORMAL' AS source,assignmentId AS localOrderId,'' AS vehicleNo FROM prepared_order
                    WHERE deviceId=? AND operatorId=?
                UNION ALL
                SELECT 'TEMP' AS source,clientOrderId AS localOrderId,vehicleNo FROM temporary_draft
                    WHERE creatorDeviceId=? AND operatorId=? AND warehouseId=?
                UNION ALL
                SELECT 'INDEPENDENT' AS source,id AS localOrderId,vehicleNo FROM independent_task
                    WHERE deviceId=? AND operatorId=? AND warehouseId=?)
                WHERE source || ':' || localOrderId > ? ORDER BY source,localOrderId LIMIT 21""",
                arrayOf(identity.deviceId, identity.operatorId, identity.deviceId, identity.operatorId, identity.warehouseId,
                    identity.deviceId, identity.operatorId, identity.warehouseId, after)).use { cursor ->
                buildList { while (cursor.moveToNext()) {
                    val source = cursor.getString(0); val id = cursor.getString(1)
                    val label = when (source) { "FORMAL" -> "正式"; "TEMP" -> "临时"; else -> "独立 · 待主管对账" }
                    add(RescueOrderChoice(RescueTarget(source, id), "$label · ${cursor.getString(2)} · $id"))
                } }
            }
    private fun scope(target: RescueTarget): JSONObject {
        if (target.source == "INDEPENDENT") return RescueIndependentSnapshot(db, RescueRows(db.openHelper.readableDatabase)).identity(target.localOrderId)
        if (target.source == "FORMAL") return RescueFormalSnapshot(db, RescueRows(db.openHelper.readableDatabase))
            .identity(checkNotNull(db.preparations().order(target.localOrderId)))
        val draft = checkNotNull(db.temporaryDrafts().get(target.localOrderId))
        return JSONObject().put("source", "TEMP").put("deviceId", draft.creatorDeviceId).put("operatorId", draft.operatorId)
            .put("warehouseId", draft.warehouseId).put("localOrderId", draft.clientOrderId)
    }
}
