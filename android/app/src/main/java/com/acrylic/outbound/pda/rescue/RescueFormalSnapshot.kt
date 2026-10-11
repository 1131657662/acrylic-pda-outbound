package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject

internal class RescueFormalSnapshot(private val db: PdaDatabase, private val rows: RescueRows) {
    fun read(id: String, grant: RescueReadGrant): RescueSnapshot {
        val prepared = checkNotNull(db.preparations().order(id)) { "原订单资料不存在" }
        val order = identity(prepared)
        grant.check(order)
        val captures = rows.read("""SELECT id,assignmentId,orderId,originDeviceId,operatorId,warehouseId,orderScanSeq,
            deviceCaptureSeq,rawInput,barcode,inputSource,confirmationPolicy,receivedWallMs,receivedElapsedMs,receivedBoot,
            snapshotId,expectedDispatchCycle,sourceRecoveryEpoch,authorizationId,assignmentEpoch,orderRevision,inputRevision,
            originAssignmentId,state,error,confirmedAt,failureAckAt FROM work_capture WHERE assignmentId=? ORDER BY orderScanSeq""", id)
        val scans = rows.read("""SELECT captureId,assignmentId,orderScanSeq,barcode,baleId,deliveryCode,model,name,rawWeight,
            weightGrams,expectedDispatchCycle,savedAt,localSeq,status FROM work_scan WHERE assignmentId=? ORDER BY orderScanSeq""", id)
        val events = rows.read("""SELECT id,assignmentId,captureId,originDeviceId,operatorId,localSeq,type,detail,wallMs,elapsedMs,
            boot,originAssignmentId,orderPosition FROM work_event WHERE assignmentId=? ORDER BY orderPosition,localSeq""", id)
        val lines = lines(prepared)
        val dispositions = rows.read("SELECT captureId,assignmentId,evidenceId,action,proofJson,ordinal FROM local_disposition WHERE assignmentId=? ORDER BY ordinal", id)
        rows.originalBytes("SELECT length(CAST(payload AS BLOB))+coalesce(length(CAST(receipt AS BLOB)),0) FROM local_submission WHERE assignmentId=? AND currentMarker=1", id)
        val submission = db.submissions().forOrder(id)
        val originals = submission?.let { RescueOriginals.frozen(it.id, it.payload, it.sha256 to it.receipt) }.orEmpty()
        return RescueSnapshot(order, captures, scans, events, lines, originals + source(prepared), dispositions)
    }
    internal fun identity(prepared: PreparedOrder): JSONObject {
        val assignment = JSONObject(prepared.assignmentJson)
        val claims = JwtVerifier.verify(assignment.getString("offlineToken"), JSONObject(prepared.signingKeyJson), "acrylic-pda-offline")
        check(claims.getString("sub") == prepared.operatorId && claims.getString("deviceId") == prepared.deviceId &&
            claims.getString("orderId") == prepared.orderId && claims.getString("assignmentId") == prepared.assignmentId)
        val work = db.work().order(prepared.assignmentId)
        val plan = JSONObject(prepared.planJson).getJSONObject("order").getJSONObject("plan")
        return JSONObject().put("source", "FORMAL").put("orderId", prepared.orderId).put("localOrderId", prepared.assignmentId)
            .put("deviceId", prepared.deviceId).put("operatorId", prepared.operatorId).put("warehouseId", claims.getString("warehouseId"))
            .put("state", work?.state ?: "PREPARED").put("vehicleNo", work?.vehicleNo ?: plan.getString("vehicleNo"))
            .put("timeTrust", "UNCONFIRMED_EXPORT_TIME")
    }
    private fun lines(prepared: PreparedOrder): List<JSONObject> {
        if (db.work().order(prepared.assignmentId) != null)
            return rows.read("SELECT assignmentId,deliveryCode,model,name,plannedCount,actualCount,weightGrams FROM work_line WHERE assignmentId=? ORDER BY deliveryCode", prepared.assignmentId)
        val lines = JSONObject(prepared.planJson).getJSONObject("order").getJSONArray("lines")
        return (0 until lines.length()).map { index -> JSONObject(lines.getJSONObject(index).toString()).apply {
            put("plannedCount", get("plannedCount").toString())
        } }
    }
    private fun source(prepared: PreparedOrder): Map<String, ByteArray> {
        val evidence = mutableListOf(RescueOriginals.publicSource("PLAN", prepared.planJson),
            RescueOriginals.publicSource("MANIFEST", prepared.manifestJson).put("signature", prepared.signedManifest),
            RescueOriginals.publicSource("SIGNING_PUBLIC_KEY", prepared.signingKeyJson),
            RescueOriginals.authorization(prepared.assignmentJson))
        db.continuations().current(prepared.assignmentId)?.let { evidence.add(RescueOriginals.authorization(it.authorizationJson)) }
        return mapOf("source-evidence.ndjson" to RescueText.jsonLines(evidence))
    }
}
