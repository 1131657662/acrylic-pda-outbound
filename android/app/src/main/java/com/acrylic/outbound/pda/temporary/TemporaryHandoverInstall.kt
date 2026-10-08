package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

/** Runs inside the receiving activation transaction; no imported device sequence advances the local counter. */
internal class TemporaryHandoverInstall(private val store: TemporaryWorkStore) {
    fun persist(stage: TemporaryHandoverStage, verified: TemporaryHandoverActivationProtocol.Verified): TemporaryDraft {
        val target = checkNotNull(store.db.temporary().byAuthorization(stage.targetAuthorizationId))
        val own = checkNotNull(store.db.temporary().request(target.requestKey))
        val command = JSONObject(own.bodyJson).put("temporaryHandoverActivation", verified.receipt)
        val request = own.copy(key = verified.decision.getString("ticketId"), bodyJson = SubmissionJson.canonical(command),
            receiptJson = SubmissionJson.canonical(verified.authorization), previousKey = null,
            signingKeyJson = SubmissionJson.canonical(TemporaryAccess(store.db, store.connection).snapshot().getJSONObject("signingKey")))
        store.db.temporary().insertRequest(request)
        val originalManifest = TemporaryCatalog.load(store.db, own).manifest
        val payloads = TemporaryParts(store.db).read(target.authorizationId, originalManifest)
        TemporaryActivation(store.db, store.connection).activate(TemporaryActivation.Input(request,
            TemporaryAccess(store.db, store.connection).snapshot(), payloads))
        return draft(stage, verified)
    }

    private fun draft(stage: TemporaryHandoverStage, verified: TemporaryHandoverActivationProtocol.Verified): TemporaryDraft {
        val body = JSONObject(stage.payloadBytes.toString(Charsets.UTF_8)); val plan = body.getJSONObject("temporaryOrder")
        val next = TemporaryDraft(verified.decision.getString("newClientOrderId"), stage.targetDeviceId, stage.targetUserId,
            stage.warehouseId, verified.authorization.getString("authorizationId"), body.optString("temporaryNo", stage.scopeId),
            verified.receipt.getString("recordedAt"), plan.getString("vehicleNo"), plan.getString("containerNo"),
            plan.getString("reason"), plan.getString("revision").toLong(), "HANDOVER_BLOCKED",
            verified.decision.getString("startOrderScanSeq").toLong(), workRound = stage.toWorkRound)
        val rows = plan.getJSONArray("lines")
        val lines = (0 until rows.length()).map { rows.getJSONObject(it) }.map {
            TemporaryLine(next.clientOrderId, it.getString("deliveryCode"), it.getString("productId"),
                it.getString("model"), it.getString("name"), TemporaryVerifier.positive(it.getString("plannedCount")))
        }
        check(lines.isNotEmpty() && lines.map { it.deliveryCode }.toSet().size == lines.size) { "交接原计划不完整" }
        val saved = next.copy(planSha256 = TemporaryPlanDigest.digest(next, lines))
        store.db.temporaryDrafts().insert(saved); store.db.temporaryDrafts().insertLines(lines)
        return saved
    }
}
