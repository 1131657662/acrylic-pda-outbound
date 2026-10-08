package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/** Synthetic signatures at the HTTP boundary; installation, counters and rollback use real Room. */
internal class TemporaryHandoverActivationFixture(root: File) : AutoCloseable {
    val confirmation = TemporaryHandoverConfirmationFixture(root, ::source)
    val stage get() = confirmation.stage
    val db get() = stage.db
    val id get() = stage.caseId
    val store get() = TemporaryWorkStore(db, stage.connection, false, true) { TimeSample(1000, 500, 1) }
    val repository get() = TemporaryHandoverActivationRepository(store, true)
    val ticket = uuid()
    val client = uuid()
    val authorization = authorization()
    var original = activation()
    init {
        confirmation.repository.queue(id, true); confirmation.repository.resume(id)
        stage.local.time = Instant.parse(JSONObject(authorization.getString("manifest")).getString("issuedAt"))
    }

    private fun source(f: TemporaryHandoverFixture) {
        val request = checkNotNull(f.db.temporary().request(f.prepared.requestKey))
        val catalog = TemporaryCatalog.load(f.db, request)
        val bale = catalog.bales.values.first { it.status == "AVAILABLE" }
        val lines = catalog.products.values.sortedBy { it.code }.map { product ->
            JSONObject().put("deliveryCode", product.code).put("productId", product.productId)
                .put("model", product.model).put("name", product.name).put("plannedCount", "3")
        }
        val plan = f.body.getJSONObject("temporaryOrder").put("lines", JSONArray(lines))
        val scan = TemporaryScan(uuid(), f.sourceClient, 1, bale.barcode, bale.baleId, bale.code,
            bale.model, bale.name, bale.rawWeight, bale.grams, bale.cycle, f.local.time.toString(), 1)
        val capture = TemporaryCapture(scan.captureId, f.sourceClient, f.sourceDevice, f.sourceUser, 1, 8,
            bale.barcode, bale.barcode, "BROADCAST", 1000, 500, 1, f.sourceAuthorization, "1",
            f.local.manifest.getString("inventorySnapshotId"), f.session.getString("serverRecoveryEpoch"), 1, bale.cycle,
            "COMMITTED", confirmedAt = f.local.time.toString())
        f.body.put("planSha256", JwtVerifier.sha(f.bytes(plan))).put("scans", JSONArray().put(TemporarySubmissionRecords.scan(scan)))
            .put("captures", JSONArray().put(TemporarySubmissionRecords.capture(capture)))
            .put("events", JSONArray()).put("captureSummary", JSONObject().put("received", "1").put("saved", "1")
                .put("voided", "0").put("pending", "0").put("invalid", "0").put("businessRejected", "0").put("discarded", "0"))
        f.resnapshot()
        f.manifest.put("checklist", JSONArray().put(JSONObject().put("baleId", bale.baleId).put("deliveryCode", bale.code)
            .put("dispatchCycle", bale.cycle.toString()).put("state", "RETAINED")))
    }

    private fun authorization(): JSONObject {
        val original = JSONObject(stage.local.receipt.toString()); val manifest = JSONObject(original.getString("manifest"))
        val identity = manifest.getJSONObject("identity").put("authorizationId", uuid()).put("bundleId", uuid())
            .put("authorizationEpoch", "99")
        val issued = stage.local.time.plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        manifest.put("issuedAt", issued.toString())
        val text = SubmissionJson.canonical(manifest); val sha = JwtVerifier.sha(text.toByteArray())
        val times = JSONObject().put("iss", "acrylic-outbound").put("iat", issued.epochSecond)
            .put("exp", Instant.parse(manifest.getString("expiresAt")).epochSecond)
        val token = JSONObject(times.toString()).put("aud", "acrylic-pda-temporary-work").put("identity", identity)
            .put("purpose", "TEMP_WORK").put("scope", "temporary_order:create pda:scan pda:submit")
            .put("jti", identity.getString("authorizationId")).put("manifestSha256", sha)
        val signed = JSONObject(times.toString()).put("aud", "acrylic-pda-temporary-preparation")
            .put("authorizationId", identity.getString("authorizationId")).put("bundleId", identity.getString("bundleId")).put("manifestSha256", sha)
        return JSONObject().put("authorizationId", identity.getString("authorizationId")).put("bundleId", identity.getString("bundleId"))
            .put("manifest", text).put("manifestSha256", sha).put("offlineToken", stage.signer.jwt(token)).put("signedManifest", stage.signer.jwt(signed))
    }

    fun activation(transform: (JSONObject) -> Unit = {}): ByteArray {
        val row = stage.repository.read(id)
        val prefix = JSONObject().put("scopeId", row.scopeId).put("submissionId", row.submissionId).put("canonicalOrderId", row.canonicalOrderId)
            .put("fromWorkRound", row.fromWorkRound.toString()).put("inputHighWatermark", row.inputHighWatermark.toString())
            .put("payloadSha256", row.payloadSha256).put("checklistSha256", JwtVerifier.sha(SubmissionJson.canonical(stage.manifest.getJSONArray("checklist")).toByteArray()))
        val context = JSONObject(stage.manifest.getJSONObject("preparationReceipt").getJSONObject("caseContext").toString())
            .put("caseVersion", "5").put("inputHighWatermark", "1").put("preparedTicketSha256", stage.manifest.getString("preparedTicketSha256"))
        val decision = JSONObject().put("kind", "TEMP_HANDOVER_ACTIVATE").put("status", "ACTIVATED").put("caseId", id)
            .put("ticketId", ticket).put("proofId", uuid()).put("fromWorkRound", "1").put("toWorkRound", "2")
            .put("newAuthorizationId", authorization.getString("authorizationId")).put("newClientOrderId", client)
            .put("targetUserId", row.targetUserId).put("targetDeviceId", row.targetDeviceId)
            .put("targetSessionId", stage.session.getString("sessionId")).put("startOrderScanSeq", "2")
            .put("retainedCount", "1").put("sourcePrefixSha256", JwtVerifier.sha(stage.bytes(prefix))).put("authorization", authorization)
        val receipt = JSONObject().put("receiptVersion", 1).put("commandType", "ACTIVATE").put("status", "RECORDED")
            .put("caseContext", context).put("businessDecision", decision).put("capabilities", stage.manifest.getJSONObject("capabilities"))
            .put("recordedAt", JSONObject(authorization.getString("manifest")).getString("issuedAt"))
        val unsigned = stage.signer.receipt(receipt).apply { remove("signedReceipt") }
        transform(unsigned)
        val claims = JSONObject().put("iss", "acrylic-outbound-server").put("aud", "acrylic-outbound-handover-receipt")
            .put("receipt", unsigned)
        return stage.bytes(unsigned.put("signedReceipt", stage.signer.jwt(claims)))
    }

    private fun uuid() = UUID.randomUUID().toString()
    fun downloadAck(change: (JSONObject) -> Unit = {}): JSONObject {
        val request = checkNotNull(db.temporary().request(ticket))
        val row = checkNotNull(db.temporary().prepared(ticket))
        val claims = JSONObject().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-temporary-activation")
            .put("authorizationId", row.authorizationId).put("bundleId", row.bundleId)
            .put("manifestSha256", row.manifestSha256).put("commandSha256", TemporaryAckVerifier.commandDigest(JSONObject(row.ackBodyJson)))
            .put("serverRecoveryEpoch", request.recoveryEpoch).put("userId", request.userId).put("deviceId", request.deviceId)
            .put("activatedAt", row.activatedAt).put("acknowledgedAt", stage.local.time.plusMillis(1).toString()).also(change)
        return JSONObject().put("authorizationId", row.authorizationId).put("bundleId", row.bundleId)
            .put("activatedAt", row.activatedAt).put("acknowledgedAt", claims.getString("acknowledgedAt"))
            .put("signedAck", stage.signer.jwt(claims))
    }

    fun acknowledge() {
        stage.local.ack = downloadAck()
        TemporaryHandoverDownload(store, true).acknowledge(client)
    }
    override fun close() = confirmation.close()
}
