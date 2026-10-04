package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.network.SessionIdentity
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Only authentication, time and HTTP are replaceable; importing always uses the real Room transaction. */
internal interface HandoverDownloadConnection {
    fun current(): JSONObject?
    fun now(): Instant
    fun call(request: Call): JSONObject
    fun download(request: Download)
    data class Call(val path: String, val expected: JSONObject, val body: JSONObject? = null)
    data class Download(val path: String, val expected: JSONObject, val target: File)
}

internal class HandoverDownloadSession(private val session: PdaSession) : HandoverDownloadConnection {
    override fun current() = session.saved()
    override fun now() = session.now()
    override fun call(request: HandoverDownloadConnection.Call): JSONObject = session.call(request.path,
        request.body, epoch = request.expected.getString("serverRecoveryEpoch"), expected = request.expected)

    override fun download(request: HandoverDownloadConnection.Download) {
        SessionIdentity.requireSame(request.expected, session.saved())
        session.network().download(request.path, request.target,
            session.headers(epoch = request.expected.getString("serverRecoveryEpoch")))
        SessionIdentity.requireSame(request.expected, session.saved())
    }
}
