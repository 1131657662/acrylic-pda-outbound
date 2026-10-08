package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.UUID

/** Synthetic command signatures; real Room installation and immutable handover originals. */
internal class TemporaryHandoverContinuationFixture(root: File) : AutoCloseable {
    val handover = TemporaryHandoverActivationFixture(root)
    val db get() = handover.db
    val store get() = TemporaryWorkStore(db, handover.stage.connection, true, true, handover.store.sample)
    val frozen: TemporarySubmission
    val draft get() = checkNotNull(db.temporaryDrafts().get(frozen.clientOrderId))
    init {
        handover.repository.install(handover.id, handover.original)
        handover.acknowledge()
        TemporaryHandoverWork(store, true).start(handover.client)
        frozen = TemporaryFreeze(store).freeze(handover.client, "合成混合接续").copy(state = "ABORTED")
        db.temporarySubmissions().progress(frozen.id, "ABORTED", null, "", false)
        store.load(frozen.clientOrderId)
    }

    fun raw(change: (JSONObject) -> Unit = {}): ByteArray {
        val receipt = receipt().also(change)
        val claims = JSONObject().put("iss", "acrylic-outbound").put("aud", "acrylic-r06-command-receipt-v1")
            .put("receipt", receipt)
        return bytes(receipt.put("signedReceipt", handover.stage.signer.jwt(claims)))
    }

    fun acknowledge(next: TemporaryDraft) {
        val link = checkNotNull(db.temporaryContinuations().forTarget(next.clientOrderId))
        val request = checkNotNull(db.temporary().request(link.ticketId))
        val row = checkNotNull(db.temporary().prepared(link.ticketId))
        val claims = JSONObject().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-temporary-activation")
            .put("authorizationId", row.authorizationId).put("bundleId", row.bundleId)
            .put("manifestSha256", row.manifestSha256)
            .put("commandSha256", TemporaryAckVerifier.commandDigest(JSONObject(row.ackBodyJson)))
            .put("serverRecoveryEpoch", request.recoveryEpoch).put("userId", request.userId).put("deviceId", request.deviceId)
            .put("activatedAt", row.activatedAt).put("acknowledgedAt", handover.stage.local.time.plusMillis(1).toString())
        handover.stage.local.ack = JSONObject().put("authorizationId", row.authorizationId).put("bundleId", row.bundleId)
            .put("activatedAt", row.activatedAt).put("acknowledgedAt", claims.get("acknowledgedAt"))
            .put("signedAck", handover.stage.signer.jwt(claims))
        TemporaryContinuationDownload(store).acknowledge(link.ticketId)
    }

    private fun authorization(): JSONObject {
        val old = handover.authorization
        val manifest = JSONObject(old.getString("manifest"))
        val expires = java.time.Instant.parse(manifest.getString("issuedAt")).plusSeconds(172800)
        manifest.put("expiresAt", expires.toString())
        val identity = manifest.getJSONObject("identity").put("authorizationId", uuid()).put("bundleId", uuid())
        identity.put("authorizationEpoch", (identity.getString("authorizationEpoch").toLong() + 1).toString())
        val text = SubmissionJson.canonical(manifest); val hash = JwtVerifier.sha(text.toByteArray(Charsets.UTF_8))
        val token = claims(old.getString("offlineToken")).put("identity", identity)
            .put("jti", identity.get("authorizationId")).put("manifestSha256", hash).put("exp", expires.epochSecond)
        val signed = claims(old.getString("signedManifest")).put("authorizationId", identity.get("authorizationId"))
            .put("bundleId", identity.get("bundleId")).put("manifestSha256", hash).put("exp", expires.epochSecond)
        return JSONObject().put("authorizationId", identity.get("authorizationId")).put("bundleId", identity.get("bundleId"))
            .put("manifest", text).put("manifestSha256", hash).put("offlineToken", handover.stage.signer.jwt(token))
            .put("signedManifest", handover.stage.signer.jwt(signed))
    }

    private fun receipt(): JSONObject {
        val auth = authorization(); val body = JSONObject(frozen.payload)
        val scans = body.getJSONArray("scans")
        val rows = JSONArray((0 until scans.length()).map { index ->
            val scan = scans.getJSONObject(index)
            JSONObject().put("baleId", scan.get("baleId")).put("expectedDispatchCycle", scan.get("expectedDispatchCycle"))
                .put("recordSha256", sha(scan))
        })
        val hash = sha(JSONObject().put("version", 1).put("items", rows)); val scope = handover.stage.scopeId
        val decision = JSONObject().put("kind", "ACTIVATE_CONTINUATION").put("status", "ACTIVATED")
            .put("ticketId", uuid()).put("scopeId", scope).put("fromWorkRound", draft.workRound.toString())
            .put("toWorkRound", (draft.workRound + 1).toString()).put("nextOrderScanSeq", draft.nextScanSeq.toString())
            .put("targetDeviceId", draft.creatorDeviceId).put("targetOperatorId", draft.operatorId)
            .put("newClientOrderId", uuid()).put("newAuthorizationId", auth.get("authorizationId"))
            .put("newBundleId", auth.get("bundleId")).put("authorization", auth)
            .put("retainedManifestVersion", "1").put("retainedManifestSha256", hash).put("retained", rows)
        return JSONObject().put("receiptVersion", 1).put("routeScope", "r06.command.v1")
            .put("receiptId", uuid()).put("commandId", uuid()).put("eventId", uuid())
            .put("resultingDecisionVersion", "3").put("commandSha256", "a".repeat(64))
            .put("recordedAt", handover.stage.local.time.toString()).put("status", "RECORDED")
            .put("commandType", "ACTIVATE_CONTINUATION").put("businessDecision", decision)
            .put("expected", JSONObject().put("retainedManifestSha256", hash).put("retainedManifestVersion", "1"))
            .put("originalContext", context(body).put("scopeId", scope)).put("actor", actor())
            .put("capabilities", JSONObject().put("writeConfirmationAvailable", false).put("releaseAllowed", false)
                .put("mayResumeScanning", false).put("stockReleased", false).put("localDeletionAllowed", false))
    }

    private fun context(body: JSONObject): JSONObject {
        val result = JSONObject().put("clientOrderId", draft.clientOrderId).put("creatorDeviceId", draft.creatorDeviceId)
            .put("originalOperatorId", draft.operatorId).put("warehouseId", draft.warehouseId).put("submissionId", frozen.id)
            .put("authorizationId", draft.authorizationId).put("receivedPayloadSha256", frozen.sha256)
            .put("declaredUploadSha256", frozen.sha256).put("planSha256", draft.planSha256).put("status", "ABORTED")
        for (field in listOf("workRound", "freezeDeviceId", "sourceRecoveryEpoch", "authorizationEpoch")) result.put(field, body.get(field))
        return result
    }

    private fun actor(): JSONObject {
        val session = handover.stage.session
        return JSONObject().put("principalId", draft.operatorId).put("deviceId", draft.creatorDeviceId)
            .put("userAuthVersion", session.get("userAuthVersion")).put("deviceVersion", session.get("deviceVersion"))
            .put("permissionSnapshot", JSONObject().put("requiredPermissions", JSONArray().put("order:resume"))
                .put("effectivePermissions", JSONArray().put("order:resume")).put("warehouseIds", JSONArray().put(draft.warehouseId)))
    }
    private fun claims(token: String) = JSONObject(String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8))
    private fun uuid() = UUID.randomUUID().toString()
    private fun bytes(value: JSONObject) = SubmissionJson.canonical(value).toByteArray(Charsets.UTF_8)
    private fun sha(value: JSONObject) = JwtVerifier.sha(bytes(value))
    override fun close() = handover.close()
}
