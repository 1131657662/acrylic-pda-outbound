package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.temporary.TemporaryAckVerifier
import org.json.JSONObject

internal class RescueTemporarySnapshot(private val db: PdaDatabase, private val rows: RescueRows) {
    fun read(id: String, grant: RescueReadGrant): RescueSnapshot {
        val draft = checkNotNull(db.temporaryDrafts().get(id)) { "原临时单不存在" }
        val preparation = checkNotNull(db.temporary().byAuthorization(draft.authorizationId))
        val request = checkNotNull(db.temporary().request(preparation.requestKey))
        TemporaryAckVerifier.declaration(preparation, request)
        check(draft.creatorDeviceId == request.deviceId && draft.operatorId == request.userId && draft.warehouseId == request.warehouseId)
        val order = JSONObject().put("source", "TEMP").put("orderId", id).put("localOrderId", id)
            .put("deviceId", draft.creatorDeviceId).put("operatorId", draft.operatorId).put("warehouseId", draft.warehouseId)
            .put("vehicleNo", draft.vehicleNo).put("containerNo", draft.containerNo).put("state", draft.state)
            .put("revision", draft.revision.toString()).put("workRound", draft.workRound.toString()).put("timeTrust", "UNCONFIRMED_EXPORT_TIME")
        grant.check(order)
        RescueTextNames.attach(order, RescueTextNames.temporary(request))
        return records(id, order, request)
    }
    private fun records(id: String, order: JSONObject, request: com.acrylic.outbound.pda.temporary.TemporaryRequest): RescueSnapshot {
        val captures = rows.read("""SELECT id,clientOrderId,originDeviceId,operatorId,orderScanSeq,deviceCaptureSeq,rawInput,barcode,
            inputSource,receivedWallMs,receivedElapsedMs,receivedBoot,authorizationId,authorizationEpoch,snapshotId,sourceRecoveryEpoch,
            orderRevision,expectedDispatchCycle,state,error,confirmedAt,failureAckAt FROM temporary_capture WHERE clientOrderId=? ORDER BY orderScanSeq""", id)
        val scans = rows.read("""SELECT captureId,clientOrderId,orderScanSeq,barcode,baleId,deliveryCode,model,name,rawWeight,
            weightGrams,expectedDispatchCycle,savedAt,localSeq,status FROM temporary_scan WHERE clientOrderId=? ORDER BY orderScanSeq""", id)
        val events = rows.read("""SELECT id,clientOrderId,captureId,originDeviceId,operatorId,localSeq,type,detail,wallMs,elapsedMs,boot
            FROM temporary_event WHERE clientOrderId=? ORDER BY localSeq""", id)
            .onEach { it.put("orderPosition", it.getString("localSeq")) }
        val lines = rows.read("SELECT clientOrderId,deliveryCode,productId,model,name,plannedCount FROM temporary_line WHERE clientOrderId=? ORDER BY deliveryCode", id)
        rows.originalBytes("SELECT length(CAST(payload AS BLOB))+coalesce(length(CAST(receipt AS BLOB)),0) FROM temporary_submission WHERE clientOrderId=?", id)
        val frozen = db.temporarySubmissions().forOrder(id)
        val originals = frozen?.let { RescueOriginals.frozen(it.id, it.payload, it.sha256 to it.receipt) }.orEmpty()
        val receipt = JSONObject(checkNotNull(request.receiptJson))
        val evidence = listOf(RescueOriginals.publicSource("MANIFEST", receipt.getString("manifest")).put("signature", receipt.getString("signedManifest")),
            RescueOriginals.publicSource("SIGNING_PUBLIC_KEY", request.signingKeyJson))
        val snapshot = RescueSnapshot(order, captures, scans, events, lines, originals +
            ("source-evidence.ndjson" to RescueText.jsonLines(evidence)))
        val prefix = RescueTemporaryPrefix(db, rows)
        return prefix.combine(prefix.read(checkNotNull(db.temporaryDrafts().get(id))), snapshot)
    }
}
