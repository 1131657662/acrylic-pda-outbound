package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.network.*
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Only the session/network boundary is replaceable; activation always uses the real Room transaction. */
interface TemporaryConnection {
    fun current(): JSONObject?
    fun now(): Instant
    fun call(request: Call): JSONObject
    fun download(request: Download)
    data class Call(val path: String, val expected: JSONObject, val body: JSONObject? = null, val key: String? = null)
    data class Download(val path: String, val expected: JSONObject, val target: File)
}

class TemporarySession(private val session: PdaSession) : TemporaryConnection {
    override fun current() = session.saved()
    override fun now() = session.now()
    override fun call(request: TemporaryConnection.Call): JSONObject = session.call(request.path, request.body,
        request.expected.getString("serverRecoveryEpoch"), extraHeaders = request.key?.let { mapOf("Idempotency-Key" to it) }.orEmpty(), expected = request.expected)
    override fun download(request: TemporaryConnection.Download) {
        SessionIdentity.requireSame(request.expected, session.saved())
        session.network().download(request.path, request.target, session.headers(epoch = request.expected.getString("serverRecoveryEpoch")))
    }
}
