package com.acrylic.outbound.pda.temporary

import org.json.JSONObject

internal class TemporarySubmissionTransport(private val connection: TemporaryConnection, private val expected: JSONObject,
    private val responses: TemporarySubmissionResponses) {
    fun send(local: TemporarySubmission): TemporarySubmissionResponses.Reply {
        val body = JSONObject(local.payload); val epoch = body.getString("sourceRecoveryEpoch")
        if (body.getString("workRound").toLong() > 1) {
            check(local.abortReason == null) { "续作取消释放政策未定义，原记录保留" }
            check(local.payload.toByteArray(Charsets.UTF_8).size <= 1024 * 1024) { "续作分块尚未接通，请保留原正文" }
            return responses.receive("pda/temporary-orders/continuation-submissions", body)
        }
        val base = "pda/submissions/${local.id}"
        local.abortReason?.let { return responses.receive("$base/abort", control(body).put("reason", it)) }
        val bytes = local.payload.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 1024 * 1024) return responses.receive("pda/temporary-orders/submissions", body)
        val manifest = control(body).put("payloadSha256", local.sha256).put("byteCount", bytes.size.toString())
            .put("chunkCount", ((bytes.size + 262143) / 262144).toString())
        val started = responses.receive("$base/upload", manifest)
        if (started.data.getString("status") !in setOf("REGISTERED", "RECEIVING")) return started
        var offset = 0; var index = 0
        while (offset < bytes.size) {
            val end = minOf(offset + 262144, bytes.size)
            connection.call(TemporaryConnection.Call("$base/chunks/$index", expected, raw = bytes.copyOfRange(offset, end), epoch = epoch))
            offset = end; index++
        }
        return responses.receive("$base/finalize", JSONObject())
    }
    private fun control(body: JSONObject) = JSONObject().apply {
        for (key in listOf("source", "assignmentId", "continuationId", "authorizationId", "freezeDeviceId", "workRound", "sourceRecoveryEpoch")) put(key, body.get(key))
    }
}
