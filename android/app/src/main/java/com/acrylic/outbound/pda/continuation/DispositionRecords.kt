package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.*
import java.util.UUID

object DispositionRecords {
    fun rows(body: JSONObject): JSONArray = if (body.has("dispositions")) body.getJSONArray("dispositions") else JSONArray()
    fun effective(body: JSONObject, proofs: JSONArray = rows(body)): List<JSONObject> {
        val excluded = (0 until proofs.length()).map { proofs.getJSONObject(it).getString("captureId") }.toSet()
        val scans = body.getJSONArray("scans")
        return (0 until scans.length()).map { scans.getJSONObject(it) }
            .filter { it.getString("status") == "ACTIVE" && it.getString("captureId") !in excluded }
    }
    fun verifyExtension(body: JSONObject, proofs: JSONArray) {
        val previous = rows(body); check(proofs.length() >= previous.length()) { "续作遗漏历史处置凭据" }
        val scans = effective(body).associateBy { it.getString("captureId") }
        val ids = mutableSetOf<String>(); val evidence = mutableSetOf<String>()
        for (i in 0 until proofs.length()) {
            val proof = proofs.getJSONObject(i); val capture = proof.getString("captureId")
            check(ids.add(capture) && evidence.add(proof.getString("approvedEvidenceId"))) { "实物处置凭据重复" }
            UUID.fromString(proof.getString("approvedEvidenceId"))
            check(proof.getString("action") in setOf("RELEASE", "QUARANTINE")) { "未知实物处置动作" }
            if (i < previous.length()) {
                check(SubmissionJson.canonical(proof) == SubmissionJson.canonical(previous.getJSONObject(i))) { "历史处置凭据被改写" }
            } else {
                val scan = checkNotNull(scans[capture]) { "处置未指向原有效装载行" }
                for (field in listOf("baleId", "barcode", "expectedDispatchCycle"))
                    check(proof.getString(field) == scan.getString(field)) { "实物处置与原包不一致：$field" }
            }
        }
    }
}
