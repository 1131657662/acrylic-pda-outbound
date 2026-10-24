package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Same real HTTP server response, consumed through the product vault/request/task/scan paths. */
internal class RescueIndependentWireFixture : AutoCloseable {
    private val vector = JSONObject(File(checkNotNull(System.getProperty("pda.temporaryFixture"))).parentFile
        .resolve("name-proof-independent-vector.json").readText())
    val receipt = vector.getJSONObject("receipt")
    val key = vector.getJSONObject("signingKey")
    val proof = IndependentCredentialProof.verify(receipt.getString("signedCredential"), key)
    private val identity = proof.identity
    var sample = TimeSample(1000, proof.issued.toEpochMilli(), 5)
    val sku = IndependentSkuFixture()
    val db get() = sku.db
    val actor = IndependentTaskActor(identity.operatorId, identity.deviceId, identity.warehouseId)
    private val session = JSONObject().put("userId", identity.operatorId).put("deviceId", identity.deviceId)
        .put("warehouseId", identity.warehouseId).put("serverRecoveryEpoch", identity.serverRecoveryEpoch)
        .put("userAuthVersion", proof.userVersion).put("deviceVersion", proof.deviceVersion)
        .put("localSessionId", UUID.randomUUID().toString()).put("signingKey", key)
        .put("timeAnchor", TrustedTime.anchor(proof.issued.toString(), sample))
    private val credentials = IndependentCredentialStore(IndependentCredentialFixture.Storage())
    private val password = "synthetic-name-proof-local-secret".toCharArray()
    private fun context() = IndependentCredentialContext(session, identity.deviceId to identity.deviceKeySha256, sample)
    private val intent = credentials.prepare(context())
    private val received = credentials.receive(IndependentCredentialIncoming(intent, receipt, context()), password)
    private val open = credentials.unlock(password, context())
    private val grant = IndependentCaptureGrant(received) { credentials.snapshot(open, context()) }
    private val imported = sku.activate("003A0000001")
    val command = IndependentTaskCommand(UUID.randomUUID().toString(), imported.imported.id, "合成车001", "只读文本互操作", mapOf("003" to 2L))
    private val repository = IndependentTaskRepository(db, { sample }) { actor }
    private val task = repository.create(command, grant)
    private val work = IndependentScanStore(db, grant, IndependentScanEnvironment({ sample }, { actor })).also { it.open(task.task.id) }
    val readGrant get() = RescueReadGrant(RescueReadIdentity(identity.deviceId, identity.operatorId, identity.warehouseId)) {}
    fun save() {
        val saving = IndependentScanSaving(work); saving.start(command.id)
        val reception = IndependentScanReception(work)
        val row = reception.receive(IndependentInput(command.id, "003A0000001", "BROADCAST", sample))
        reception.validate(row.capture.id); check(saving.save(row.capture.id) == "SAVED")
    }
    fun expireAndReopen() {
        sample = sample.copy(elapsed = sample.elapsed + 3 * 86_400_000L, wall = sample.wall + 3 * 86_400_000L)
        credentials.revoke(); sku.reopen()
        check(runCatching { credentials.validate(open, context()) }.isFailure)
    }
    fun read() = RescueRoomSnapshot(db).read(RescueTarget("INDEPENDENT", command.id), readGrant)
    override fun close() = sku.close()
}
