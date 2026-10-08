package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Network-boundary fixture signs only a generated recipient receipt; product Room operations remain real. */
internal class TemporaryHandoverConfirmationFixture(root: File,
    configure: (TemporaryHandoverFixture) -> Unit = {}) : AutoCloseable {
    val stage = TemporaryHandoverFixture(root)
    val db get() = stage.db
    val id get() = stage.caseId
    val commands = mutableListOf<ByteArray>()
    var response: ByteArray? = null
    var afterResponse: () -> Unit = {}
    var transform: (JSONObject) -> Unit = {}
    val connection = object : TemporaryConnection by stage.connection {
        override fun original(request: TemporaryConnection.Original): ByteArray {
            check(request.path.endsWith("/temporary-confirmation"))
            val command = checkNotNull(request.body); commands.add(command.clone())
            if (response == null) response = signed(JSONObject(command.toString(Charsets.UTF_8)), request.epoch)
            afterResponse()
            return checkNotNull(response).clone()
        }
    }
    val repository get() = TemporaryHandoverConfirmationRepository(db, connection, true)
    init {
        if (!stage.session.has("sessionId")) stage.session.put("sessionId", UUID.randomUUID().toString())
        configure(stage); stage.repository.stage(id)
    }

    private fun signed(command: JSONObject, epoch: String): ByteArray {
        val bound = JSONObject().put("caseId", id).put("transportRecoveryEpoch", epoch).put("command", command)
        val receipt = JSONObject().put("receiptVersion", 1).put("receiptId", UUID.randomUUID().toString())
            .put("caseId", id).put("status", "TARGET_CONFIRMED").put("recordedAt", stage.local.time.toString())
            .put("commandSha256", JwtVerifier.sha(stage.bytes(bound))).put("originalCommand", bound)
            .put("preparedTicket", stage.manifest.getJSONObject("preparedTicket"))
            .put("checklist", stage.manifest.getJSONArray("checklist")).put("capabilities", stage.manifest.getJSONObject("capabilities"))
        val actor = JSONObject()
        for (field in listOf("userId", "deviceId", "warehouseId", "userAuthVersion", "deviceVersion", "sessionId")) actor.put(field, stage.session.getString(field))
        receipt.put("actor", actor); transform(receipt)
        val token = stage.signer.jwt(JSONObject().put("iss", "acrylic-outbound")
            .put("aud", "acrylic-pda-temporary-handover-confirmation").put("receipt", receipt))
        return SubmissionJson.canonical(receipt.put("signedReceipt", token)).toByteArray(Charsets.UTF_8)
    }
    override fun close() = stage.close()
}
