package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

internal object TemporaryArchiveContext {
    fun request(db: PdaDatabase, id: String, epoch: String, receiptKey: JSONObject): TemporaryArchiveRequest {
        val local = checkNotNull(db.temporarySubmissions().get(id))
        val success = checkNotNull(TemporarySuccessRepository(db).read(id, receiptKey)) { "缺少已核验的原成功回执" }
        val body = JSONObject(local.payload); val draft = checkNotNull(db.temporaryDrafts().get(local.clientOrderId))
        val preparation = checkNotNull(db.temporary().byAuthorization(body.getString("authorizationId")))
        val source = checkNotNull(db.temporary().request(preparation.requestKey))
        check(draft.authorizationId == preparation.authorizationId && draft.creatorDeviceId == source.deviceId &&
            draft.operatorId == source.userId && draft.warehouseId == source.warehouseId && draft.planSha256 == body.getString("planSha256"))
        check(body.getString("freezeDeviceId") == source.deviceId && body.getString("sourceRecoveryEpoch") == source.recoveryEpoch && epoch == source.recoveryEpoch)
        val receiptText = SubmissionJson.canonical(success)
        val request = JSONObject().put("payloadSha256", local.sha256).put("receiptSha256", TemporaryArchiveVerifier.digest(success))
        val expected = JSONObject(request.toString()).put("archiveVersion", 2).put("submissionId", id)
            .put("orderId", success.getString("orderId")).put("assignmentId", JSONObject.NULL)
            .put("authorizationId", preparation.authorizationId).put("source", body.getJSONObject("source"))
            .put("warehouseId", source.warehouseId).put("operatorId", source.userId).put("freezeDeviceId", source.deviceId)
            .put("workRound", "1").put("sourceRecoveryEpoch", source.recoveryEpoch).put("recoveryEpoch", epoch)
            .put("payloadBytes", local.payload.toByteArray(Charsets.UTF_8).size.toString())
            .put("receiptBytes", receiptText.toByteArray(Charsets.UTF_8).size.toString()).put("mappingVersion", "1")
            .put("planSha256", body.getString("planSha256")).put("completedAt", success.getString("completedAt"))
            .put("storagePolicy", "SERVER_DURABLE_FILES_V1").put("localDeletionAllowed", false)
        return TemporaryArchiveRequest(id, epoch, SubmissionJson.canonical(request), SubmissionJson.canonical(expected))
    }
}
