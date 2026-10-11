package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.network.PdaNetwork
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Retained actual LOGIN tokens; only vault/Keystore boundary is supplied by the isolated input. */
internal class TemporaryRecoveredHandoverHttpConnection(private val input: JSONObject, private val output: File) : TemporaryConnection {
    private val session = input.getJSONObject("session")
    private val network = PdaNetwork(input.getString("baseUrl"))
    private var ordinal = 0
    var losePayload = true
    init { check(input.getString("baseUrl") == "http://127.0.0.1:19434/api/v1/") }
    override fun current() = session
    override fun now(): Instant = Instant.now()
    override fun localDraftStopKey() = input.getString("devicePublicKey")
    override fun original(request: TemporaryConnection.Original): ByteArray {
        val bytes = network.original(request.path, request.body, mapOf("Authorization" to "Bearer ${session.getString("accessToken")}",
            "X-Server-Recovery-Epoch" to request.epoch), request.raw, request.maximumBytes)
        File(output, "original-${++ordinal}.json").writeBytes(bytes)
        File(output, "transport.tsv").appendText("${request.path}\t${bytes.size}\n")
        if (request.path.endsWith("recovery-package") && losePayload) {
            losePayload = false; throw java.io.IOException("Injected loss after actual HTTP payload response")
        }
        return bytes
    }
    override fun call(request: TemporaryConnection.Call): JSONObject = error("Recovery download cannot issue commands")
    override fun download(request: TemporaryConnection.Download) = error("Recovery download must retain original bytes")
    override fun stopProof(facts: com.acrylic.outbound.pda.evidence.E2aStopProof.Facts): com.acrylic.outbound.pda.evidence.E2aStopProof.Signed =
        error("Recipient cannot issue originating STOP")
    override fun signLocalDraftStop(stop: com.acrylic.outbound.pda.evidence.LocalDraftStop): com.acrylic.outbound.pda.evidence.LocalDraftStopProof.Signed =
        error("Recipient cannot sign originating STOP")
}
