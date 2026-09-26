package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.security.MessageDigest

/** Length-independent canonical record frames bind identity, order, bytes and metadata. */
internal class LocalDraftStopDigest(metadata: String) {
    private val digest = MessageDigest.getInstance("SHA-256")
    var count = 0L
        private set
    var bytes = metadata.toByteArray(Charsets.UTF_8).size.toLong()
        private set

    init {
        check(SubmissionJson.canonical(JSONObject(metadata)) == metadata && bytes in 1..131072)
        digest.update("acrylic-pda-local-draft-stop-v1\n$metadata\n".toByteArray(Charsets.UTF_8))
    }

    fun append(row: LocalDraftStopItem) {
        check(row.ordinal == count + 1 && row.rawBytes.size in 1..33554432 && row.deviceSeq > 0)
        check(row.kind in setOf("CAPTURE", "SCAN", "AUDIT", "ARCHIVE"))
        E2aItemValidation.uuid(row.recordId)
        check(E2aWire.sha(row.rawBytes) == row.bytesSha256)
        val record = JSONObject(row.rawBytes.toString(Charsets.UTF_8))
        check(SubmissionJson.canonical(record).toByteArray(Charsets.UTF_8).contentEquals(row.rawBytes))
        if (row.kind != "ARCHIVE") fields(row, record)
        bytes = Math.addExact(bytes, row.rawBytes.size.toLong()); count++
        check(bytes <= 134217728 && count <= 250000) { "停止证据超过本机资源限额，原记录保留" }
        val frame = JSONObject().put("ordinal", row.ordinal.toString()).put("kind", row.kind)
            .put("recordId", row.recordId).put("deviceSeq", row.deviceSeq.toString())
            .put("recordStatus", row.recordStatus).put("bytesSha256", row.bytesSha256)
            .put("byteCount", row.rawBytes.size.toString())
        digest.update((SubmissionJson.canonical(frame) + "\n").toByteArray(Charsets.UTF_8))
    }

    fun finish(): String = digest.digest().joinToString("") { "%02x".format(it) }

    private fun fields(row: LocalDraftStopItem, record: JSONObject) {
        check(record.getString(if (row.kind == "SCAN") "captureId" else "id") == row.recordId)
        check(record.getString(if (row.kind == "CAPTURE") "deviceCaptureSeq" else "localSeq").toLong() == row.deviceSeq)
        check(record.getString(when (row.kind) { "CAPTURE" -> "state"; "SCAN" -> "status"; else -> "type" }) == row.recordStatus)
    }
}
