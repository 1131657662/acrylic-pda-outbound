package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.network.PdaNetwork
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Only login and clock are synthetic; all preparation and evidence downloads use the live isolated HTTP server. */
internal class TemporaryHandoverHttpConnection(input: JSONObject, private val output: File) : TemporaryConnection {
    val exerciseWork = input.optBoolean("exerciseWork", false)
    val largePayload = input.optBoolean("largePayload", false)
    val shortShipmentReason = input.optString("shortShipmentReason", "")
    val scanCount = input.optInt("scanCount", 1)
    private val privateKey = input.optString("devicePrivateKey")
    private val publicKey = input.optString("devicePublicKey")
    var session = JSONObject(input.getJSONObject("session").toString()).put("localSessionId", "isolated-temp-recipient")
    private val network = PdaNetwork(input.getString("baseUrl"))
    private var ordinal = 0
    var loseAck = true
    var losePackage = true
    var loseConfirmation = true
    var loseActivation = true
    var loseSubmission = true
    var loseSuccess = true
    var loseStop = true
    init { check(input.getString("baseUrl") == "http://127.0.0.1:19434/api/v1/") }
    override fun current() = session
    override fun now(): Instant = Instant.now()
    private fun headers(epoch: String, key: String? = null) = mapOf("Authorization" to "Bearer ${session.getString("accessToken")}",
        "X-Server-Recovery-Epoch" to epoch) + key?.let { mapOf("Idempotency-Key" to it) }.orEmpty()

    override fun call(request: TemporaryConnection.Call): JSONObject {
        val response = network.request(request.path, request.body, headers(request.epoch, request.key), request.raw).getJSONObject("data")
        recordTransport(request.path, request.raw?.size ?: request.body?.toString()?.toByteArray(Charsets.UTF_8)?.size ?: 0, response.optString("status"))
        File(output, "call-${++ordinal}.json").writeText(response.toString())
        if (request.path.endsWith("download-ack") && loseAck) {
            loseAck = false; throw java.io.IOException("Injected lost actual target ACK response")
        }
        return response
    }
    override fun original(request: TemporaryConnection.Original): ByteArray {
        val bytes = network.original(request.path, request.body, headers(request.epoch), request.raw, request.maximumBytes)
        recordTransport(request.path, request.body?.size ?: request.raw?.size ?: 0, JSONObject(bytes.toString(Charsets.UTF_8)).optJSONObject("data")?.optString("status").orEmpty())
        File(output, "original-${++ordinal}.json").writeBytes(bytes)
        request.body?.let { File(output, "command-$ordinal.json").writeBytes(it) }
        if (request.path.endsWith("/stop") && request.path.contains("handover-submissions/") && loseStop) {
            loseStop = false; throw java.io.IOException("Injected lost actual independent handover STOP response")
        }
        val handoverRead = request.path.contains("handover-submissions/")
        val handoverAccepted = request.path.endsWith("handover-submissions") && request.body != null ||
            handoverRead && JSONObject(bytes.toString(Charsets.UTF_8)).getJSONObject("data").optString("status") == "REVIEW_REQUIRED"
        if (handoverAccepted && loseSubmission) {
            loseSubmission = false; throw java.io.IOException("Injected lost actual handover acceptance response")
        }
        if (request.path.contains("handover-submissions/") && JSONObject(bytes.toString(Charsets.UTF_8)).getJSONObject("data").optString("status") == "SUCCEEDED" && loseSuccess) {
            loseSuccess = false; throw java.io.IOException("Injected lost actual handover success response")
        }
        if (request.path.endsWith("/manifest") && JSONObject(bytes.toString(Charsets.UTF_8)).optString("status") == "ACTIVATED" && loseActivation) {
            loseActivation = false; throw java.io.IOException("Injected lost actual target activation response")
        }
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
    private fun recordTransport(path: String, size: Int, status: String) {
        File(output, "transport.tsv").appendText("$path\t$size\t$status\n")
    }
    override fun stopProof(facts: com.acrylic.outbound.pda.evidence.E2aStopProof.Facts): com.acrylic.outbound.pda.evidence.E2aStopProof.Signed {
        check(exerciseWork && facts.freezeDeviceId == session.getString("deviceId"))
        return com.acrylic.outbound.pda.evidence.E2aStopProof.Signed(publicKey,
            com.acrylic.outbound.pda.evidence.E2aStopProof.sign(facts, publicKey, ::sign))
    }
    private fun sign(text: String): String {
        val key = java.security.KeyFactory.getInstance("EC").generatePrivate(
            java.security.spec.PKCS8EncodedKeySpec(java.util.Base64.getDecoder().decode(privateKey)))
        return java.security.Signature.getInstance("SHA256withECDSA").run {
            initSign(key); update(text.toByteArray(Charsets.UTF_8)); java.util.Base64.getEncoder().encodeToString(sign())
        }
    }
    override fun localDraftStopKey(): String = error("Target stage cannot issue source STOP")
    override fun signLocalDraftStop(stop: com.acrylic.outbound.pda.evidence.LocalDraftStop): com.acrylic.outbound.pda.evidence.LocalDraftStopProof.Signed = error("Target stage cannot issue source STOP")
}
