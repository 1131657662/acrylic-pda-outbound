package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject

/** Public identifiers are device-attested; server authorization is reconciled against its original ID. */
internal class RescueIndependentOriginals(private val db: PdaDatabase, private val rows: RescueRows) {
    fun read(id: String): Map<String, ByteArray> {
        val task = checkNotNull(db.independentTasks().task(id))
        val proof = IndependentCredentialProof.verify(task.signedCredential, JSONObject(task.trustedKeyJson))
        check(proof.id == task.credentialId && proof.identity == IndependentCaptureIdentity(task.operatorId, task.deviceId,
            task.warehouseId, proof.identity.deviceKeySha256, task.recoveryEpoch))
        val original = IndependentSkuBytes.original(db.independentSku(), task.skuSha256)
        val preview = IndependentSkuParser.parse(original); check(preview.errorRows == 0)
        val imported = rows.read("SELECT * FROM independent_sku_import WHERE id=?", task.importId).single()
        val decision = rows.read("SELECT * FROM independent_sku_decision WHERE importId=?", task.importId).single()
        check(imported.getString("sha256") == task.skuSha256 && decision.getString("outcome") == "ACTIVATED" &&
            (preview.duplicateRows == 0 || decision.getString("duplicatesConfirmed") == "1"))
        val publicTask = rows.read("""SELECT id,importId,skuSha256,credentialId,operatorId,deviceId,warehouseId,recoveryEpoch,
            vehicleNo,reason,plannedTotal,commandSha256,createdWall,createdElapsed,createdBoot FROM independent_task WHERE id=?""", id).single()
        val anchor = JSONObject(task.timeAnchorJson)
        val created = TrustedTime.now(anchor, TimeSample(task.createdElapsed, task.createdWall, task.createdBoot)); proof.at(created)
        publicTask.put("createdTrustedAt", created.toString())
        val source = JSONObject().put("purpose", "INDEPENDENT_READONLY_SOURCE_V1").put("task", publicTask)
            .put("import", imported).put("decision", decision).put("credential", credential(proof))
            .put("timeAnchor", JSONObject().apply { put("server", anchor.getString("server"))
                listOf("wall", "elapsed", "boot").forEach { put(it, anchor.get(it).toString()) } })
        return files(id, source, original)
    }
    private fun files(id: String, source: JSONObject, original: ByteArray): Map<String, ByteArray> {
        RescuePublicEvidence.verify(source); rows.reserve(source.toString())
        val voids = rows.read("SELECT * FROM independent_void WHERE taskId=? ORDER BY elapsed,captureId", id)
        val state = rows.read("SELECT * FROM independent_task_state WHERE taskId=?", id).single()
        return mapOf("independent-sku.bin" to original, "independent-source.json" to source.toString().toByteArray(Charsets.UTF_8),
            "independent-voids.ndjson" to RescueText.jsonLines(voids), "independent-state.json" to state.toString().toByteArray(Charsets.UTF_8))
    }
    private fun credential(proof: IndependentCredentialProof) = JSONObject().put("credentialId", proof.id)
        .put("operatorId", proof.identity.operatorId).put("deviceId", proof.identity.deviceId).put("warehouseId", proof.identity.warehouseId)
        .put("deviceKeySha256", proof.identity.deviceKeySha256).put("serverRecoveryEpoch", proof.identity.serverRecoveryEpoch)
        .put("userAuthVersion", proof.userVersion).put("deviceVersion", proof.deviceVersion).put("issuedAt", proof.issued.toString())
        .put("expiresAt", proof.expires.toString()).put("credentialSha256", RescueBundle.sha(proof.originalToken.toByteArray(Charsets.UTF_8)))
}
