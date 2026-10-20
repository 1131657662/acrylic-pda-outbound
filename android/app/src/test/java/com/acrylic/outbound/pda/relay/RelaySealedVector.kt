package com.acrylic.outbound.pda.relay

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.LocalSubmission
import com.acrylic.outbound.pda.temporary.TemporarySuccessJson
import org.json.JSONObject
import java.io.File

/** External sealed test evidence; no package-supplied key establishes trust. */
internal class RelaySealedVector(private val root: File) {
    private val manifest = TemporarySuccessJson.parse(File(root, "inputs.json").readBytes())
    private val names = setOf("order.json", "submission.json", "device-public-key.der",
        "returned.pda-receipt.json", "process-response.json", "receipt-key.json")
    private val originals = names.associateWith { File(root, it).readBytes() }
    val identity get() = TemporarySuccessJson.parse(bytes("order.json"))
    val payload get() = bytes("submission.json").toString(Charsets.UTF_8)
    val key get() = TemporarySuccessJson.parse(bytes("receipt-key.json"))
    val device get() = bytes("device-public-key.der")
    val returned get() = bytes("returned.pda-receipt.json")
    val processed get() = TemporarySuccessJson.parse(bytes("process-response.json"))

    init {
        check(manifest.keys().asSequence().toSet() == names)
        names.forEach { check(JwtVerifier.sha(bytes(it)) == manifest.getString(it)) }
    }

    fun bytes(name: String): ByteArray = checkNotNull(originals[name]).copyOf()

    fun local(): LocalSubmission {
        val body = JSONObject(payload)
        return LocalSubmission(body.getString("submissionId"), body.getString("assignmentId"),
            payload, JwtVerifier.sha(bytes("submission.json")))
    }

    fun unchanged() = names.forEach { check(File(root, it).readBytes().contentEquals(bytes(it))) }
}
