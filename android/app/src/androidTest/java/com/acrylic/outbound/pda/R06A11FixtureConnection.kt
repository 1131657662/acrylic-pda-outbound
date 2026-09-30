package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.evidence.E2aStopProof
import com.acrylic.outbound.pda.evidence.LocalDraftStop
import com.acrylic.outbound.pda.temporary.TemporaryConnection
import org.json.JSONObject
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Only the session/network/key boundary is synthetic; product verification and Room stay real. */
internal class R06A11FixtureConnection(
    private val fixture: JSONObject,
    val signer: R06A11StopSigner,
    savedSession: JSONObject? = null
) : TemporaryConnection {
    val session = savedSession ?: JSONObject(fixture.getJSONObject("session").toString())
        .put("signingKey", fixture.getJSONObject("signingKey"))
        .put("localSessionId", UUID.randomUUID().toString())
    private val manifest = JSONObject(fixture.getJSONObject("receipt").getString("manifest"))
    var receive: (TemporaryConnection.Original) -> ByteArray = { error("Unexpected original request") }
    override fun current() = session
    override fun now(): Instant = Instant.parse(fixture.getJSONObject("activationCommand").getString("activatedAt"))
    override fun localDraftStopKey() = signer.publicKey
    override fun signLocalDraftStop(stop: LocalDraftStop) = signer.sign(stop)
    override fun stopProof(facts: E2aStopProof.Facts): E2aStopProof.Signed = error("Frozen path is outside this fixture")
    override fun original(request: TemporaryConnection.Original) = receive(request)

    override fun call(request: TemporaryConnection.Call): JSONObject = when {
        request.path == "pda/devices/me" -> JSONObject()
        request.path.endsWith("baseline") -> JSONObject()
            .put("warehouseId", manifest.getJSONObject("identity").getString("warehouseId"))
            .put("inventorySnapshotId", manifest.getString("inventorySnapshotId"))
            .put("mappingRevision", manifest.getString("mappingRevision"))
            .put("policy", JSONObject().put("version", manifest.getString("policyVersion")))
        request.path.endsWith("download-ack") -> fixture.getJSONObject("activationReceipt")
        request.path == "pda/temporary-preparations" -> fixture.getJSONObject("receipt")
        else -> error("Unexpected preparation path: ${request.path}")
    }

    override fun download(request: TemporaryConnection.Download) {
        val name = request.path.substringAfterLast('/').substringBefore('?')
        request.target.writeBytes(Base64.getDecoder().decode(fixture.getJSONObject("gzip").getString(name)))
    }
}
