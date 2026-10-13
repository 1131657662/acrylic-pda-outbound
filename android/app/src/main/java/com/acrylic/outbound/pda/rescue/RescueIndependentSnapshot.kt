package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.independent.*
import org.json.JSONObject

/** Read existing facts after expiry/reboot without opening a work store or granting collection. */
internal class RescueIndependentSnapshot(private val db: PdaDatabase, private val rows: RescueRows) {
    fun identity(id: String): JSONObject {
        val task = checkNotNull(db.independentTasks().task(id)) { "原独立任务不存在，请保留资料" }
        return JSONObject().put("source", "INDEPENDENT").put("orderId", task.id).put("localOrderId", task.id)
            .put("operatorId", task.operatorId).put("deviceId", task.deviceId).put("warehouseId", task.warehouseId)
            .put("vehicleNo", task.vehicleNo).put("state", checkNotNull(db.independentTasks().state(id)).state)
            .put("timeTrust", "UNCONFIRMED_EXPORT_TIME").put("pendingReconciliation", true)
            .put("serverAuthorizationVerified", false)
    }
    fun read(id: String, grant: RescueReadGrant): RescueSnapshot {
        val order = identity(id); grant.check(order)
        val captures = rows.read("""SELECT c.*,c.sequence AS orderScanSeq,c.deviceSequence AS deviceCaptureSeq,
            s.state,s.error,s.confirmedAt,s.failureAckAt FROM independent_capture c
            INNER JOIN independent_capture_state s ON s.captureId=c.id WHERE c.taskId=? ORDER BY c.sequence""", id)
        val scans = rows.read("""SELECT s.*,s.sequence AS orderScanSeq,s.savedTrustedAt AS savedAt,
            CASE WHEN v.captureId IS NULL THEN 'ACTIVE' ELSE 'VOID' END AS status FROM independent_scan s
            LEFT JOIN independent_void v ON v.captureId=s.captureId WHERE s.taskId=? ORDER BY s.sequence""", id)
        val events = rows.read("SELECT *,sequence AS orderPosition FROM independent_task_event WHERE taskId=? ORDER BY sequence", id)
        val lines = rows.read("SELECT * FROM independent_task_line WHERE taskId=? ORDER BY deliveryCode", id)
        val originals = RescueIndependentOriginals(db, rows).read(id)
        val snapshot = RescueSnapshot(order, captures, scans, events, lines, originals)
        RescueIndependentValidation.verify(snapshot)
        val facts = RescueFacts.summary(snapshot); val state = checkNotNull(db.independentTasks().state(id))
        check(facts.getString("active").toLong() == state.actualCount && facts.getString("weightGrams").toLong() == state.weightGrams &&
            db.independentScans().missingGuards(id) == 0 && db.independentScans().invalidGuards(id) == 0) { "原独立明细与汇总不一致，请保全原库" }
        check(state.nextScanSeq == captures.size.toLong() + 1 && state.nextEventSeq == events.size.toLong() + 1)
        return snapshot
    }
}
