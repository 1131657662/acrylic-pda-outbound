package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.LocalRecoveryControl
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Synthetic signed documents and session boundary; native Room remains real. No original HTTP claim. */
internal class TemporaryRecoveredHandoverFixture(root: File) : AutoCloseable {
    val base = TemporaryHandoverFixture(root)
    val local get() = base.local
    val db get() = base.db
    val session get() = base.session
    val caseId get() = base.caseId
    val body get() = base.body
    var manifest: JSONObject
        get() = base.manifest
        set(value) { base.manifest = value }
    val qualification get() = manifest.getJSONObject("preparedTicket").getJSONObject("temporaryQualification")
    val repository get() = TemporaryRecoveredHandoverRepository(db, base.connection, true)
    init {
        val bale = id()
        body.put("scans", JSONArray().put(JSONObject().put("baleId", bale).put("deliveryCode", "003")
            .put("expectedDispatchCycle", "1").put("status", "ACTIVE")))
        base.resnapshot(); TemporaryHandoverFaultFixture.install(base)
        manifest.put("checklist", JSONArray().put(JSONObject().put("baleId", bale).put("deliveryCode", "003")
            .put("dispatchCycle", "1").put("state", "RETAINED")))
        session.put("serverRecoveryEpoch", "2").put("sessionId", id()).put("accessExpiresAt", local.time.plusSeconds(600).toString())
            .put("capabilities", JSONObject().put("recoveryRead", true).put("temporaryWork", false))
        db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "2", mode = "RECONCILING"))
        manifest.put("recipientSessionSha256", "e".repeat(64))
        manifest.getJSONObject("preparedTicket").put("temporaryQualification", description())
        val actor = manifest.getJSONObject("faultReceipt").getJSONObject("actor").getString("principalId")
        manifest.getJSONObject("approvalReceipt").put("actor", JSONObject().put("principalId", actor))
        manifest.getJSONObject("preparationReceipt").getJSONObject("caseContext").put("approvedBy", actor)
        resign()
    }
    private fun description(): JSONObject {
        val source = JSONObject().put("authorizationId", base.sourceAuthorization).put("clientOrderId", base.sourceClient)
            .put("submissionId", base.submissionId).put("workRound", "1").put("sourceRecoveryEpoch", "1")
            .put("inputHighWatermark", "1").put("payloadSha256", JwtVerifier.sha(base.bytes(body)))
        val recipient = JSONObject().put("deviceId", session.getString("deviceId")).put("userId", session.getString("userId"))
            .put("deviceVersion", session.getString("deviceVersion")).put("userAuthVersion", session.getString("userAuthVersion"))
            .put("keySha256", JwtVerifier.sha(local.localDraftStopKey().toByteArray(Charsets.UTF_8)))
        val control = JSONObject().put("installationId", id()).put("epochId", id()).put("mysqlServerUuid", id())
            .put("databaseSchema", "synthetic_recovery").put("controlRevision", "2").put("transportRecoveryEpoch", "2")
            .put("controlRecordSha256", "a".repeat(64))
        val baseline = JSONObject().put("inventorySnapshotId", local.manifest.getString("inventorySnapshotId"))
            .put("inventoryVersion", "1").put("inventorySha256", "b".repeat(64)).put("mappingRevision", "1")
            .put("policyId", id()).put("policyVersion", local.manifest.getString("policyVersion"))
        return JSONObject().put("purpose", TemporaryRecoveredHandoverQualification.PURPOSE).put("qualificationId", id())
            .put("qualificationSha256", "d".repeat(64)).put("caseId", caseId).put("scopeId", base.scopeId)
            .put("warehouseId", session.getString("warehouseId")).put("source", source).put("recipient", recipient)
            .put("control", control).put("baseline", baseline).put("expiresAt", local.time.plusSeconds(3600).toString())
            .put("capabilities", JSONArray().put("PREPARE").put("DOWNLOAD").put("CONFIRM"))
    }
    fun resign() {
        val ticket = manifest.getJSONObject("preparedTicket")
        val sha = JwtVerifier.sha(base.bytes(ticket))
        manifest.put("preparedTicketSha256", sha)
        manifest.getJSONObject("preparationReceipt").getJSONObject("businessDecision")
            .put("temporaryQualification", JSONObject(qualification.toString())).put("preparedTicketSha256", sha)
        base.resign("approvalReceipt"); base.resign("preparationReceipt")
    }
    private fun id() = UUID.randomUUID().toString()
    override fun close() = base.close()
}
