package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Synthetic network signatures only; recovery originals, intent and receipt use native Room. */
internal class TemporaryRecoveredConfirmationFixture(root: File) : AutoCloseable {
    val source = TemporaryRecoveredHandoverFixture(root)
    val db get() = source.db
    val id get() = source.caseId
    val commands = mutableListOf<ByteArray>()
    var response: ByteArray? = null
    var afterResponse: () -> Unit = {}
    var transform: (JSONObject) -> Unit = {}
    var audience = "acrylic-pda-recovered-temporary-handover-confirmation"
    val connection = object : TemporaryConnection by source.base.connection {
        override fun original(request: TemporaryConnection.Original): ByteArray {
            check(request.path == "pda/handovers/$id/recovered-confirmation")
            check(request.epoch == source.session.getString("serverRecoveryEpoch"))
            val bytes = checkNotNull(request.body); commands.add(bytes.clone())
            if (response == null) response = signed(JSONObject(bytes.toString(Charsets.UTF_8)))
            afterResponse(); return checkNotNull(response).clone()
        }
    }
    val repository get() = TemporaryRecoveredConfirmationRepository(db, connection, true)
    init { source.repository.stage(id) }
    private fun signed(command: JSONObject): ByteArray {
        val manifest = source.manifest
        val receipt = JSONObject().put("formatVersion", 1).put("purpose", "RECOVERED_TEMP_HANDOVER_CONFIRMATION")
            .put("status", "RECOVERED_TARGET_CONFIRMED").put("receiptId", UUID.randomUUID().toString())
            .put("recordedAt", source.local.time.toString()).put("requestId", "synthetic-recovery-confirmation")
            .put("commandSha256", JwtVerifier.sha(source.base.bytes(command))).put("recipientSessionSha256", manifest.getString("recipientSessionSha256"))
            .put("originalCommand", command).put("qualification", source.qualification)
            .put("preparedTicket", manifest.getJSONObject("preparedTicket")).put("checklist", manifest.getJSONArray("checklist"))
            .put("capabilities", manifest.getJSONObject("capabilities"))
        val actor = JSONObject()
        for (field in listOf("userId", "deviceId", "warehouseId", "userAuthVersion", "deviceVersion", "sessionId")) actor.put(field, source.session.getString(field))
        receipt.put("actor", actor); transform(receipt)
        val token = source.base.signer.jwt(JSONObject().put("iss", "acrylic-outbound").put("aud", audience).put("receipt", receipt))
        return SubmissionJson.canonical(receipt.put("signedReceipt", token)).toByteArray(Charsets.UTF_8)
    }
    override fun close() = source.close()
}
