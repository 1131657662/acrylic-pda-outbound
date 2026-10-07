package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.network.PdaNetwork
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Only login and clock are synthetic; all preparation and evidence downloads use the live isolated HTTP server. */
internal class TemporaryHandoverHttpConnection(input: JSONObject, private val output: File) : TemporaryConnection {
    var session = JSONObject(input.getJSONObject("session").toString()).put("localSessionId", "isolated-temp-recipient")
    private val network = PdaNetwork(input.getString("baseUrl"))
    private var ordinal = 0
    var loseAck = true
    var losePackage = true
    var loseConfirmation = true
    init { check(input.getString("baseUrl") == "http://127.0.0.1:19434/api/v1/") }
    override fun current() = session
    override fun now(): Instant = Instant.now()
    private fun headers(epoch: String, key: String? = null) = mapOf("Authorization" to "Bearer ${session.getString("accessToken")}",
        "X-Server-Recovery-Epoch" to epoch) + key?.let { mapOf("Idempotency-Key" to it) }.orEmpty()

    override fun call(request: TemporaryConnection.Call): JSONObject {
        val response = network.request(request.path, request.body, headers(request.epoch, request.key), request.raw).getJSONObject("data")
        File(output, "call-${++ordinal}.json").writeText(response.toString())
        if (request.path.endsWith("download-ack") && loseAck) {
            loseAck = false; throw java.io.IOException("Injected lost actual target ACK response")
        }
        return response
    }
    override fun original(request: TemporaryConnection.Original): ByteArray {
        val bytes = network.original(request.path, request.body, headers(request.epoch), request.raw, request.maximumBytes)
        File(output, "original-${++ordinal}.json").writeBytes(bytes)
        request.body?.let { File(output, "command-$ordinal.json").writeBytes(it) }
        if (request.path.endsWith("temporary-confirmation") && loseConfirmation) {
            loseConfirmation = false; throw java.io.IOException("Injected lost actual target confirmation response")
        }
        if (request.path.endsWith("recovery-package") && losePackage) {
            losePackage = false; throw java.io.IOException("Injected lost actual original payload response")
        }
        return bytes
    }
    override fun download(request: TemporaryConnection.Download) {
        network.download(request.path, request.target, headers(request.expected.getString("serverRecoveryEpoch")))
    }
    override fun stopProof(facts: com.acrylic.outbound.pda.evidence.E2aStopProof.Facts): com.acrylic.outbound.pda.evidence.E2aStopProof.Signed = error("Target stage cannot issue source STOP")
    override fun localDraftStopKey(): String = error("Target stage cannot issue source STOP")
    override fun signLocalDraftStop(stop: com.acrylic.outbound.pda.evidence.LocalDraftStop): com.acrylic.outbound.pda.evidence.LocalDraftStopProof.Signed = error("Target stage cannot issue source STOP")
}
