package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

internal object E2aTransferRequests {
    fun identity(stop: LocalE2aStop) = E2aWire.Identity(stop.manifestId, stop.snapshotId,
        stop.authorizationId, stop.submissionId, stop.workRound)

    fun open(stop: LocalE2aStop): JSONObject = JSONObject().put("operation", "OPEN")
        .put("manifestId", stop.manifestId).put("manifestVersion", 1).put("snapshotId", stop.snapshotId)
        .put("source", "TEMP").put("sourceId", stop.authorizationId)
        .put("submissionId", stop.submissionId).put("workRound", stop.workRound)

    fun page(stop: LocalE2aStop, page: E2aWire.Page): JSONObject = JSONObject().put("operation", "PAGE")
        .put("openOriginalKey", stop.openOriginalKey).put("pageHash", page.hash)
        .put("pageCore", JSONObject(page.canonicalCore))

    fun raw(stop: LocalE2aStop, item: JSONObject): JSONObject = JSONObject()
        .put("warehouseId", stop.warehouseId).put("submissionId", stop.submissionId).put("workRound", stop.workRound)
        .put("openOriginalKey", stop.openOriginalKey).put("originalObjectId", item.getString("originalObjectId"))
        .put("kind", item.getString("objectKind")).put("byteCount", item.getLong("byteCount"))
        .put("bytesSha256", item.getString("itemBytesSha256"))

    fun facts(stop: LocalE2aStop, manifest: E2aWire.Manifest) = E2aStopProof.Facts(stop.warehouseId,
        stop.authorizationId, stop.submissionId, stop.workRound, stop.originalOperatorId, stop.freezeDeviceId,
        stop.sourceRecoveryEpoch, stop.stopEventId, stop.captureHighWatermark, stop.auditThroughSeq,
        1, stop.manifestId, 1, manifest.bytesHash, manifest.setHash)

    fun stop(stop: LocalE2aStop, manifest: E2aWire.Manifest, proof: JSONObject): JSONObject = JSONObject()
        .put("operation", "STOP").put("openOriginalKey", stop.openOriginalKey)
        .put("manifestCore", JSONObject(manifest.canonicalCore)).put("manifestBytesHash", manifest.bytesHash)
        .put("setHash", manifest.setHash).put("stopProof", proof)

    fun verify(stop: LocalE2aStop, manifest: E2aWire.Manifest, stopKey: String): JSONObject = JSONObject()
        .put("operation", "VERIFY").put("openOriginalKey", stop.openOriginalKey).put("stopOriginalKey", stopKey)
        .put("manifestId", stop.manifestId).put("manifestVersion", 1).put("setHash", manifest.setHash)
        .put("verificationVersion", 1).put("algorithmVersion", 1)

    fun saved(id: String, operation: String, ordinal: Long, key: String, body: JSONObject): LocalE2aRequest {
        E2aItemValidation.uuid(key)
        val canonical = SubmissionJson.canonical(body)
        check(canonical.toByteArray(Charsets.UTF_8).size <= 262144)
        return LocalE2aRequest(id, operation, ordinal, key, canonical, E2aWire.sha(canonical.toByteArray(Charsets.UTF_8)))
    }

    fun requireBody(row: LocalE2aRequest, body: JSONObject) {
        check(row == saved(row.clientOrderId, row.operation, row.ordinal, row.originalKey, body)) {
            "原请求字节或摘要已变化，请保留资料核对"
        }
    }
}
