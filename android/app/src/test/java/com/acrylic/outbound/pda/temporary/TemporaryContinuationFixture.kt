package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.continuation.HandoverImportSigning
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.UUID

/** Synthetic consumer fixture only; these signatures are not real HTTP or original-device evidence. */
internal class TemporaryContinuationFixture(root: File) : AutoCloseable {
    private val signer = HandoverImportSigning()
    val work = TemporaryWorkFixture(root, contract = contract())
    val db get() = work.db
    val draft get() = checkNotNull(db.temporaryDrafts().get(work.id))
    val frozen: TemporarySubmission
    val receipt: JSONObject
    val key get() = signer.key

    init {
        work.start(); work.scan()
        frozen = TemporaryFreeze(work.store).freeze(work.id, "合成续作测试").copy(state = "ABORTED")
        db.temporarySubmissions().progress(frozen.id, frozen.state, frozen.receipt, "", false)
        receipt = receipt()
    }

    fun input() = TemporaryContinuationProtocol.Input(draft, frozen, work.catalog.manifest,
        checkNotNull(work.fixture.auth), key)
    fun raw(change: (JSONObject) -> Unit = {}): ByteArray {
        val value = JSONObject(receipt.toString()).also(change)
        val claims = JSONObject().put("iss", "acrylic-outbound").put("aud", "acrylic-r06-command-receipt-v1").put("receipt", value)
        val token = signer.jwt(claims)
        return SubmissionJson.canonical(value.put("signedReceipt", token)).toByteArray(Charsets.UTF_8)
    }
    fun activate(raw: ByteArray = raw()) = TemporaryContinuationActivation(work.store).activate(work.id, raw)
    fun downloadAck(request: TemporaryRequest): JSONObject {
        val row = checkNotNull(db.temporary().prepared(request.key))
        val body = JSONObject(row.ackBodyJson)
        val claims = JSONObject().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-temporary-activation")
            .put("authorizationId", row.authorizationId).put("bundleId", row.bundleId)
            .put("manifestSha256", row.manifestSha256).put("commandSha256", TemporaryAckVerifier.commandDigest(body))
            .put("serverRecoveryEpoch", request.recoveryEpoch).put("userId", request.userId).put("deviceId", request.deviceId)
            .put("activatedAt", row.activatedAt).put("acknowledgedAt", work.fixture.time.plusMillis(1).toString())
        return JSONObject().put("authorizationId", row.authorizationId).put("bundleId", row.bundleId)
            .put("activatedAt", row.activatedAt).put("acknowledgedAt", claims.getString("acknowledgedAt"))
            .put("signedAck", signer.jwt(claims))
    }
    fun rehash(decision: JSONObject) {
        decision.put("retainedManifestSha256", sha(JSONObject().put("version", 1).put("items", decision.getJSONArray("retained"))))
    }

    private fun contract(): JSONObject {
        val value = JSONObject(File(checkNotNull(System.getProperty("pda.temporaryFixture"))).readText())
        value.put("signingKey", signer.key)
        val old = value.getJSONObject("receipt")
        for (field in listOf("offlineToken", "signedManifest")) old.put(field, signer.jwt(claims(old.getString(field))))
        val ack = value.getJSONObject("activationReceipt")
        ack.put("signedAck", signer.jwt(claims(ack.getString("signedAck"))))
        return value
    }

    private fun authorization(): JSONObject {
        val old = work.fixture.receipt; val manifest = JSONObject(old.getString("manifest"))
        val identity = manifest.getJSONObject("identity")
        val authorization = UUID.randomUUID().toString(); val bundle = UUID.randomUUID().toString()
        identity.put("authorizationId", authorization).put("bundleId", bundle)
            .put("authorizationEpoch", (identity.getString("authorizationEpoch").toLong() + 1).toString())
        val text = SubmissionJson.canonical(manifest); val hash = JwtVerifier.sha(text.toByteArray())
        val offline = claims(old.getString("offlineToken")).put("jti", authorization).put("identity", identity).put("manifestSha256", hash)
        val signed = claims(old.getString("signedManifest")).put("authorizationId", authorization).put("bundleId", bundle).put("manifestSha256", hash)
        return JSONObject().put("authorizationId", authorization).put("bundleId", bundle).put("manifest", text)
            .put("manifestSha256", hash).put("offlineToken", signer.jwt(offline)).put("signedManifest", signer.jwt(signed))
    }

    private fun receipt(): JSONObject {
        val auth = authorization(); val body = JSONObject(frozen.payload); val session = checkNotNull(work.fixture.auth)
        val scan = body.getJSONArray("scans").getJSONObject(0); val scope = UUID.randomUUID().toString()
        val rows = JSONArray().put(JSONObject().put("baleId", scan.get("baleId"))
            .put("expectedDispatchCycle", scan.get("expectedDispatchCycle")).put("recordSha256", sha(scan)))
        val decision = JSONObject().put("kind", "ACTIVATE_CONTINUATION").put("status", "ACTIVATED")
            .put("ticketId", UUID.randomUUID().toString()).put("scopeId", scope).put("fromWorkRound", "1").put("toWorkRound", "2")
            .put("nextOrderScanSeq", draft.nextScanSeq.toString()).put("targetDeviceId", draft.creatorDeviceId)
            .put("targetOperatorId", draft.operatorId).put("newClientOrderId", UUID.randomUUID().toString())
            .put("newAuthorizationId", auth.get("authorizationId")).put("newBundleId", auth.get("bundleId")).put("authorization", auth)
            .put("retainedManifestVersion", "1").put("retained", rows).also(::rehash)
        val actor = JSONObject().put("principalId", draft.operatorId).put("deviceId", draft.creatorDeviceId)
            .put("userAuthVersion", session.get("userAuthVersion")).put("deviceVersion", session.get("deviceVersion"))
            .put("permissionSnapshot", JSONObject().put("requiredPermissions", JSONArray().put("order:resume"))
                .put("effectivePermissions", JSONArray().put("order:resume")).put("warehouseIds", JSONArray().put(draft.warehouseId)))
        return JSONObject().put("receiptVersion", 1).put("routeScope", "r06.command.v1")
            .put("receiptId", UUID.randomUUID().toString()).put("commandId", UUID.randomUUID().toString())
            .put("eventId", UUID.randomUUID().toString()).put("resultingDecisionVersion", "3").put("commandSha256", "a".repeat(64))
            .put("recordedAt", work.fixture.time.toString()).put("expected", JSONObject()
                .put("retainedManifestSha256", decision.get("retainedManifestSha256")).put("retainedManifestVersion", "1"))
            .put("status", "RECORDED").put("commandType", "ACTIVATE_CONTINUATION").put("businessDecision", decision)
            .put("originalContext", context(body).put("scopeId", scope)).put("actor", actor)
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

    private fun claims(token: String) = JSONObject(String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8))
    private fun sha(value: JSONObject) = JwtVerifier.sha(SubmissionJson.canonical(value).toByteArray(Charsets.UTF_8))
    override fun close() = work.close()
}
