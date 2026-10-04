package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.PdaNetwork
import com.acrylic.outbound.pda.sync.RecoveryProtection
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant

/** Synthetic session/time boundary only. All material and acknowledgements use production HTTP. */
internal class HandoverRealHttpConnection(input: JSONObject, private val database: () -> PdaDatabase) : HandoverDownloadConnection {
    private val session = input.getJSONObject("session")
    private val network = PdaNetwork(input.getString("baseUrl")) {
        RecoveryProtection(database()).observe(it, session.getString("serverRecoveryEpoch"))
    }
    var failDownload = true
    var loseAck = true
    var downloads = 0
    val ackBodies = mutableListOf<String>()
    val ackReceipts = mutableListOf<String>()

    init { check(input.getString("baseUrl") == "http://127.0.0.1:19434/api/v1/") }

    override fun current() = session
    override fun now(): Instant = Instant.now()

    override fun call(request: HandoverDownloadConnection.Call): JSONObject {
        check(!database().inTransaction())
        val data = network.request(request.path, request.body, headers()).getJSONObject("data")
        if (request.path.endsWith("/download-ack")) {
            ackBodies.add(checkNotNull(request.body).toString())
            ackReceipts.add(data.toString())
            if (loseAck) { loseAck = false; throw IOException("Injected lost acknowledgement after server commit") }
        }
        return data
    }

    override fun download(request: HandoverDownloadConnection.Download) {
        check(!database().inTransaction())
        network.download(request.path, request.target, headers())
        downloads++
        if (failDownload) { failDownload = false; throw IOException("Injected interrupted content download") }
    }

    fun stage(caseId: String): LocalHandoverStage {
        val path = "pda/handovers/$caseId"
        val manifest = network.original("$path/manifest", null, headers(), maximumBytes = 2 * 1024 * 1024)
        val bytes = network.original("$path/recovery-package", null, headers(), maximumBytes = 32 * 1024 * 1024)
        val verified = HandoverPackageVerifier.verify(JSONObject(manifest.toString(Charsets.UTF_8)), bytes, session)
        check(verified.caseId == caseId)
        saveVerifiedHandoverStage(database(), verified, session)
        return checkNotNull(database().handoverStages().get(caseId))
    }

    private fun headers() = mapOf("Authorization" to "Bearer ${session.getString("accessToken")}",
        "X-Server-Recovery-Epoch" to session.getString("serverRecoveryEpoch"))
}
