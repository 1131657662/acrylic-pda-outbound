package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.independent.IndependentSkuParser
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject
import java.time.Instant
import java.time.Duration

/** Fail generation on damaged local originals; no repair, signature replacement or business write. */
internal object RescueIndependentValidation {
    fun verify(snapshot: RescueSnapshot) {
        val source = JSONObject(snapshot.originals.getValue("independent-source.json").toString(Charsets.UTF_8))
        val task = source.getJSONObject("task"); val credential = source.getJSONObject("credential")
        val sku = IndependentSkuParser.parse(snapshot.originals.getValue("independent-sku.bin")).rows
            .filter { it.errorCode == null }.associateBy { checkNotNull(it.parsedFields).barcode }
        verifyCaptures(snapshot, source)
        verifyScans(snapshot, task, credential, sku)
        verifyPlans(snapshot, task, sku.values.map { checkNotNull(it.parsedFields) }.associateBy { it.deliveryCode })
        verifyVoids(snapshot, source)
        RescueIndependentAudit.verify(snapshot, source)
    }
    private fun verifyCaptures(snapshot: RescueSnapshot, source: JSONObject) {
        val task = source.getJSONObject("task"); val credential = source.getJSONObject("credential")
        snapshot.captures.forEachIndexed { index, row ->
            check(row.getString("taskId") == task.getString("id") && row.getString("deviceId") == task.getString("deviceId"))
            check(row.getLong("sequence") == index + 1L && row.getLong("orderScanSeq") == index + 1L)
            check(row.getString("state") in setOf("RECEIVED", "WAITING_CONFIRM", "CONFIRM_REQUESTED", "COMMITTED", "INVALID", "BUSINESS_REJECTED", "DISCARDED"))
            check(row.getString("deviceSequence") == row.getString("deviceCaptureSeq"))
            check(index == 0 || row.getLong("deviceSequence") > snapshot.captures[index - 1].getLong("deviceSequence"))
            check(TrustedTime.now(source.getJSONObject("timeAnchor"), TimeSample(row.getLong("elapsed"), row.getLong("wall"), row.getInt("boot"))) == at(row.getString("capturedTrustedAt"), credential))
        }
    }
    private fun verifyScans(snapshot: RescueSnapshot, task: JSONObject, credential: JSONObject,
        sku: Map<String, com.acrylic.outbound.pda.independent.IndependentSkuRow>) {
        val captures = snapshot.captures.associateBy { it.getString("id") }
        snapshot.scans.forEach { row ->
            val original = checkNotNull(sku[row.getString("barcode")]); val bale = checkNotNull(original.parsedFields)
            val capture = checkNotNull(captures[row.getString("captureId")])
            check(row.getString("taskId") == task.getString("id") && row.getString("barcode") == capture.getString("barcode"))
            check(row.getString("deliveryCode") == bale.deliveryCode && row.getString("model") == bale.model && row.getString("name") == bale.name)
            check(row.getString("rawWeight") == bale.rawWeight && row.getString("weightGrams") == bale.weightGrams &&
                row.getInt("skuLineNo") == original.lineNo && row.getString("skuRawText") == original.rawText)
            check(capture.getString("state") == "COMMITTED" && at(row.getString("savedAt"), credential) >= at(capture.getString("confirmedAt"), credential))
        }
    }
    private fun verifyPlans(snapshot: RescueSnapshot, task: JSONObject,
        products: Map<String, com.acrylic.outbound.pda.independent.IndependentSkuBale>) {
        check(snapshot.lines.sumOf { it.getLong("plannedCount") } == task.getLong("plannedTotal"))
        snapshot.lines.forEach { line ->
            val product = checkNotNull(products[line.getString("deliveryCode")])
            check(line.getString("taskId") == task.getString("id") && line.getString("model") == product.model &&
                line.getString("name") == product.name && line.getLong("plannedCount") > 0)
            check(snapshot.scans.count { it.getString("deliveryCode") == product.deliveryCode && it.getString("status") == "ACTIVE" } <= line.getLong("plannedCount"))
        }
    }
    private fun verifyVoids(snapshot: RescueSnapshot, source: JSONObject) {
        val text = snapshot.originals.getValue("independent-voids.ndjson").toString(Charsets.UTF_8)
        val voids = text.lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()
        check(voids.map { it.getString("captureId") }.toSet().size == voids.size)
        check(voids.map { it.getString("captureId") }.toSet() == snapshot.scans.filter { it.getString("status") == "VOID" }.map { it.getString("captureId") }.toSet())
        voids.forEach { row ->
            check(row.getString("taskId") == snapshot.order.getString("localOrderId") && row.getString("reason").isNotBlank())
            val sampled = TrustedTime.now(source.getJSONObject("timeAnchor"), TimeSample(row.getLong("elapsed"), row.getLong("wall"), row.getInt("boot")))
            val authorized = at(row.getString("authorizedAt"), source.getJSONObject("credential"))
            check(authorized >= sampled && Duration.between(sampled, authorized) <= Duration.ofMinutes(2))
        }
    }
    private fun at(text: String, credential: JSONObject) = Instant.parse(text).also {
        check(it >= Instant.parse(credential.getString("issuedAt")) && it < Instant.parse(credential.getString("expiresAt")))
    }
}
