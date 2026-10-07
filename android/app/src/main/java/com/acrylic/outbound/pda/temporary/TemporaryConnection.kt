package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.evidence.E2aStopProof
import com.acrylic.outbound.pda.evidence.LocalDraftStop
import com.acrylic.outbound.pda.evidence.LocalDraftStopProof
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Only the session/network boundary is replaceable; activation always uses the real Room transaction. */
interface TemporaryConnection {
    fun current(): JSONObject?
    fun now(): Instant
    fun call(request: Call): JSONObject
    fun original(request: Original): ByteArray
    fun download(request: Download)
    fun stopProof(facts: E2aStopProof.Facts): E2aStopProof.Signed
    fun localDraftStopKey(): String
    fun signLocalDraftStop(stop: LocalDraftStop): LocalDraftStopProof.Signed
    data class Call(val path: String, val expected: JSONObject, val body: JSONObject? = null, val key: String? = null,
        val raw: ByteArray? = null, val epoch: String = expected.getString("serverRecoveryEpoch"))
    data class Download(val path: String, val expected: JSONObject, val target: File)
    data class Original(val path: String, val expected: JSONObject, val body: ByteArray? = null,
        val epoch: String = expected.getString("serverRecoveryEpoch"), val raw: ByteArray? = null,
        val maximumBytes: Int = 262144)
}

class TemporarySession(private val session: PdaSession) : TemporaryConnection {
    override fun localDraftStopKey() = session.localDraftStopKey()
    override fun signLocalDraftStop(stop: LocalDraftStop) = session.signLocalDraftStop(stop)
    override fun original(request: TemporaryConnection.Original) = session.original(
        PdaSession.Original(request.path, request.body, request.epoch, request.raw, request.maximumBytes), request.expected)
    override fun stopProof(facts: E2aStopProof.Facts) = session.stopProof(facts)
    override fun current() = session.saved()
    override fun now() = session.now()
    override fun call(request: TemporaryConnection.Call): JSONObject = session.call(request.path, request.body,
        request.epoch, raw = request.raw, extraHeaders = request.key?.let { mapOf("Idempotency-Key" to it) }.orEmpty(), expected = request.expected)
    override fun download(request: TemporaryConnection.Download) {
        SessionIdentity.requireSame(request.expected, session.saved())
        session.network().download(request.path, request.target, session.headers(epoch = request.expected.getString("serverRecoveryEpoch")))
    }
}
