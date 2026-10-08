package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

internal object TemporaryHandoverRetained {
    fun scans(stage: TemporaryHandoverStage): List<JSONObject> {
        val body = JSONObject(stage.payloadBytes.toString(Charsets.UTF_8))
        val rows = body.getJSONArray("scans")
        val scans = (0 until rows.length()).map { rows.getJSONObject(it) }
        val checklist = JSONObject(stage.manifestJson).getJSONArray("checklist")
        val retained = (0 until checklist.length()).map { checklist.getJSONObject(it) }.filter { it.getString("state") == "RETAINED" }
        check(retained.map { it.getString("baleId") }.toSet().size == retained.size)
        return retained.map { line ->
            val row = scans.single { it.getString("baleId") == line.getString("baleId") &&
                it.getString("expectedDispatchCycle") == line.getString("dispatchCycle") && it.getString("status") == "ACTIVE" }
            check(row.getString("deliveryCode") == line.getString("deliveryCode"))
            TemporaryVerifier.uuid(row.getString("captureId")); TemporaryVerifier.positive(row.getString("orderScanSeq"))
            check(row.getString("barcode").matches(Regex("[A-Z0-9]{11}")))
            TemporaryVerifier.positive(row.getString("weightGrams")); row
        }.also { check(it.map { row -> row.getString("barcode") }.toSet().size == it.size) }
    }

    fun guard(stage: TemporaryHandoverStage, scan: JSONObject) = TemporaryHandoverGuard(scan.getString("barcode"),
        stage.caseId, scan.getString("captureId"), TemporaryVerifier.positive(scan.getString("expectedDispatchCycle")),
        JwtVerifier.sha(SubmissionJson.canonical(scan).toByteArray(Charsets.UTF_8)))

    fun install(db: PdaDatabase, stage: TemporaryHandoverStage) {
        for (scan in scans(stage)) {
            val barcode = scan.getString("barcode")
            check(db.scans().guard(barcode) == null && db.temporaryScans().guard(barcode) == null &&
                db.temporaryHandoverGuards().get(barcode) == null) { "接收机已有条码占用，保留暂存资料并核对原订单" }
            db.temporaryHandoverGuards().insert(guard(stage, scan))
        }
    }
}
