package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.work.*
import okhttp3.mockwebserver.MockResponse
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.UUID

internal class HandoverContinuationFixture(directory: File) : AutoCloseable {
    val download = HandoverDownloadFixture(directory)
    val local get() = download.local
    val bodies = java.util.concurrent.CopyOnWriteArrayList<String>()
    val receipt: JSONObject
    var respond: () -> MockResponse = { json(receipt) }
    val access = object : WorkAccess {
        override fun identity(order: PreparedOrder) {
            check(order.operatorId == download.auth!!.getString("userId") && order.deviceId == download.auth!!.getString("deviceId"))
        }
        override fun authorize(order: PreparedOrder): java.time.Instant {
            identity(order)
            JwtVerifier.authorization(JSONObject(order.assignmentJson), download.auth!!, local.input.key, download.clock)
            return download.clock
        }
        override fun sample() = TimeSample(2000, download.clock.toEpochMilli(), 1)
    }
    val store get() = WorkStore(local.db, access)

    init {
        local.install()
        download.ack()
        receipt = receipt()
        download.continuation = { request ->
            val body = request.body.readUtf8()
            check(local.db.handoverContinuations().get(local.caseId)?.commandId == JSONObject(body).getString("commandId"))
            bodies.add(body)
            respond()
        }
    }

    fun activate() = HandoverContinuationRepository(local.db, download.connection).activate(local.caseId)
    fun resign() {
        receipt.remove("signedReceipt")
        receipt.put("signedReceipt", local.signing.jwt(JSONObject().put("iss", "acrylic-outbound")
            .put("aud", "acrylic-pda-handover-continuation").put("receipt", JSONObject(receipt.toString()))))
    }

    private fun receipt(): JSONObject {
        val assignment = JSONObject(local.input.assignment.toString())
        val previous = assignment.getString("offlineAuthorizationId")
        val auth = UUID.randomUUID().toString()
        val claims = JSONObject(String(Base64.getUrlDecoder().decode(assignment.getString("offlineToken").split('.')[1])))
            .put("jti", auth).put("scope", "pda:scan").put("renewedFromAuthorizationId", previous)
            .put("authorizationSequence", 1).put("iat", download.clock.epochSecond)
        assignment.put("offlineAuthorizationId", auth).put("offlineToken", local.signing.jwt(claims))
            .put("issuedAt", download.clock.toString()).put("authorizationSequence", "1")
        val result = JSONObject().put("caseId", local.caseId).put("continuationId", UUID.randomUUID().toString())
            .put("status", "ACTIVATED").put("scope", "pda:scan").put("assignmentId", local.target)
            .put("authorizationId", auth).put("previousAssignmentId", local.target).put("previousAuthorizationId", previous)
            .put("recoveryPackageSha256", local.db.handoverStages().get(local.caseId)!!.recoverySha256)
            .put("retainedManifestSha256", ContinuationVerifier.retainedHash(local.body))
            .put("nextOrderScanSeq", local.body.getLong("captureHighWatermark") + 1)
            .put("expiresAt", assignment.getString("expiresAt")).put("stockReleased", false)
            .put("targetDeviceId", assignment.getString("deviceId")).put("targetUserId", assignment.getString("operatorId"))
            .put("signedToken", assignment.getString("offlineToken")).put("assignment", assignment)
        return result.put("signedReceipt", local.signing.jwt(JSONObject().put("iss", "acrylic-outbound")
            .put("aud", "acrylic-pda-handover-continuation").put("receipt", JSONObject(result.toString()))))
    }

    fun json(data: JSONObject) = MockResponse().setHeader("Content-Type", "application/json").setHeader("Cache-Control", "no-store")
        .setBody(data.toString())
    override fun close() = download.close()
}
