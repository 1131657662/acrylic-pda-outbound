package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.evidence.E2aStopProof
import com.acrylic.outbound.pda.evidence.LocalDraftStop
import com.acrylic.outbound.pda.network.PdaNetwork
import com.acrylic.outbound.pda.network.SessionIdentity
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant

/** Existing synthetic session; only GET for the original batch is available after restore. */
internal class ArchiveRecoveryQueryConnection(private val input: JSONObject, private val id: String,
                                              private val attempt: File) : TemporaryConnection {
    private val auth = JSONObject(input.getJSONObject("session").toString())
        .put("signingKey", input.getJSONObject("signingKey")).put("localSessionId", "isolated-original-query")
    var gets = 0
        private set
    override fun current() = auth
    override fun now(): Instant = Instant.parse(input.getJSONObject("activationCommand").getString("activatedAt"))
    override fun original(request: TemporaryConnection.Original): ByteArray {
        SessionIdentity.requireSame(request.expected, auth)
        check(request.path == "pda/submissions/$id" && request.body == null && request.raw == null)
        val headers = mapOf("Authorization" to "Bearer ${input.getString("accessToken")}",
            "X-Server-Recovery-Epoch" to request.epoch)
        val raw = PdaNetwork(input.getString("baseUrl")).original(request.path, null, headers)
        val file = File(attempt, "lost-success-query-${++gets}.bin"); check(file.createNewFile()); file.writeBytes(raw)
        if (gets == 1) throw IOException("Injected lost original success response before product retention")
        return raw
    }
    override fun call(request: TemporaryConnection.Call): JSONObject = error("No new command during original recovery")
    override fun download(request: TemporaryConnection.Download): Unit = error("No new preparation during original recovery")
    override fun stopProof(facts: E2aStopProof.Facts): E2aStopProof.Signed = error("Original signer unavailable")
    override fun localDraftStopKey(): String = error("Original signer unavailable")
    override fun signLocalDraftStop(stop: LocalDraftStop): com.acrylic.outbound.pda.evidence.LocalDraftStopProof.Signed =
        error("Original signer unavailable")
}
