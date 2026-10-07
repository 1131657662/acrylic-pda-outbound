package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.network.PdaNetwork
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant

internal class TemporaryContinuationHttpConnection(private val fixture: TemporaryFixture,
    input: JSONObject, private val evidence: File) : TemporaryConnection by fixture {
    private val network = PdaNetwork(input.getString("baseUrl"))
    private val token = input.getString("token")
    private var ordinal = 0
    var loseAck = true
    var loseIntake = true
    val ackRequests = mutableListOf<Pair<String?, String>>()
    override fun now(): Instant = Instant.now()
    private fun headers(epoch: String, key: String? = null) = mapOf("Authorization" to "Bearer $token",
        "X-Server-Recovery-Epoch" to epoch) + key?.let { mapOf("Idempotency-Key" to it) }.orEmpty()

    override fun call(request: TemporaryConnection.Call): JSONObject {
        check(request.path.endsWith("/download-ack"))
        ackRequests.add(request.key to checkNotNull(request.body).toString())
        val result = network.request(request.path, request.body, headers(request.epoch, request.key)).getJSONObject("data")
        File(evidence, "client-${++ordinal}-ack.json").writeText(result.toString())
        if (loseAck) { loseAck = false; throw IOException("Deliberately lost actual HTTP acknowledgement before Room persistence") }
        return result
    }

    override fun original(request: TemporaryConnection.Original): ByteArray {
        request.body?.let { File(evidence, "client-${++ordinal}-upload.json").writeBytes(it) }
        val bytes = network.original(request.path, request.body, headers(request.epoch), request.raw)
        File(evidence, "client-${++ordinal}-response.json").writeBytes(bytes)
        if (request.body != null && loseIntake) {
            loseIntake = false; throw IOException("Deliberately lost actual HTTP intake response before Room persistence")
        }
        return bytes
    }
}
