package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.data.PdaDatabase
import java.util.concurrent.Callable
import org.json.JSONObject

data class RescueOrderChoice(val target: RescueTarget, val label: String)
data class RescueOrderPage(val rows: List<RescueOrderChoice>, val nextCursor: String?)

private data class RescueOrderCandidate(val target: RescueTarget, val recordedAt: String,
    val vehicleNo: String, val orderNo: String, val planJson: String, val sortTime: Long) {
    fun cursor() = JSONObject().put("time", sortTime).put("source", target.source).put("id", target.localOrderId).toString()
}

class RescueOrders(private val db: PdaDatabase) {
    fun page(grant: RescueReadGrant, after: String = ""): RescueOrderPage {
        grant.recheck()
        return db.runInTransaction(Callable {
            val candidates = candidates(grant.identity, after)
            val permitted = candidates.take(20).mapNotNull { permitted(it, grant) }
            grant.recheck()
            RescueOrderPage(permitted, if (candidates.size > 20) candidates[19].cursor() else null)
        })
    }
    fun find(grant: RescueReadGrant, target: RescueTarget): RescueOrderChoice? {
        grant.recheck()
        return db.runInTransaction(Callable {
            val choice = candidates(grant.identity, "", target).singleOrNull()?.let { permitted(it, grant) }
            grant.recheck()
            choice
        })
    }
    private fun permitted(candidate: RescueOrderCandidate, grant: RescueReadGrant): RescueOrderChoice? = runCatching {
        val identity = scope(candidate.target)
        grant.check(identity)
        RescueOrderChoice(candidate.target, RescueOrderLabel.format(candidate.target, candidate.recordedAt,
            identity.optString("vehicleNo", candidate.vehicleNo), candidate.orderNo, candidate.planJson))
    }.getOrNull()

    private fun candidates(identity: RescueReadIdentity, after: String, target: RescueTarget? = null): List<RescueOrderCandidate> {
        val key = if (after.isEmpty()) null else JSONObject(after)
        val time = key?.getLong("time") ?: Long.MAX_VALUE
        val source = key?.getString("source").orEmpty(); val id = key?.getString("id").orEmpty()
        val args = arrayOf<Any>(identity.deviceId, identity.operatorId, identity.deviceId, identity.operatorId, identity.warehouseId,
            identity.deviceId, identity.operatorId, identity.warehouseId, after, time, time, source, source, id,
            target?.source.orEmpty(), target?.source.orEmpty(), target?.localOrderId.orEmpty())
        return db.openHelper.readableDatabase.query(QUERY, args).use { cursor ->
            buildList { while (cursor.moveToNext()) {
                add(RescueOrderCandidate(RescueTarget(cursor.getString(0), cursor.getString(1)), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getLong(6)))
            } }
        }
    }
    private companion object {
        // Unify ISO dates and wall times in milliseconds. Stable source/id ties also govern pagination.
        const val QUERY = """SELECT * FROM (SELECT *, CASE WHEN source='INDEPENDENT' THEN CAST(recordedAt AS INTEGER)
            ELSE coalesce(CAST(round((julianday(recordedAt)-2440587.5)*86400000) AS INTEGER),-9223372036854775808) END AS sortTime FROM (
                SELECT 'FORMAL' AS source,assignmentId AS localOrderId,activatedAt AS recordedAt,'' AS vehicleNo,
                    '' AS orderNo,planJson FROM prepared_order WHERE deviceId=? AND operatorId=?
                UNION ALL
                SELECT 'TEMP',clientOrderId,createdAt,vehicleNo,temporaryNo,'' FROM temporary_draft
                    WHERE creatorDeviceId=? AND operatorId=? AND warehouseId=?
                UNION ALL
                SELECT 'INDEPENDENT',id,CAST(createdWall AS TEXT),vehicleNo,'','' FROM independent_task
                    WHERE deviceId=? AND operatorId=? AND warehouseId=?))
                WHERE (?='' OR sortTime<? OR (sortTime=? AND (source>? OR (source=? AND localOrderId>?))))
                    AND (?='' OR (source=? AND localOrderId=?))
                ORDER BY sortTime DESC,source,localOrderId LIMIT 21"""
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
