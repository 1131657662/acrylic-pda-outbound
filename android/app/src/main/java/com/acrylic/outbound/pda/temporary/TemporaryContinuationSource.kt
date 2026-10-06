package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject

/** Binds a signed activation to the original stopped source and retained scan bytes. */
internal object TemporaryContinuationSource {
    fun verify(receipt: JSONObject, input: TemporaryContinuationProtocol.Input) {
        val draft = input.draft; val frozen = input.frozen; val body = JSONObject(frozen.payload)
        check(draft.state == "FROZEN" && frozen.state == "ABORTED" && frozen.clientOrderId == draft.clientOrderId)
        check(SubmissionJson.canonical(body) == frozen.payload && sha(body) == frozen.sha256)
        check(body.get("submissionId") == frozen.id && body.get("authorizationId") == draft.authorizationId)
        check(body.get("workRound") == draft.workRound.toString() && body.get("planSha256") == draft.planSha256)
        val source = body.getJSONObject("source"); check(source.get("kind") == "TEMP")
        check(source.get("clientOrderId") == draft.clientOrderId && source.get("creatorDeviceId") == draft.creatorDeviceId)
        check(body.get("freezeDeviceId") == draft.creatorDeviceId)
        val identity = input.manifest.getJSONObject("identity")
        check(identity.get("authorizationId") == draft.authorizationId && identity.get("userId") == draft.operatorId)
        check(identity.get("deviceId") == draft.creatorDeviceId && identity.get("warehouseId") == draft.warehouseId)
        val context = receipt.getJSONObject("originalContext")
        mapOf("clientOrderId" to draft.clientOrderId, "creatorDeviceId" to draft.creatorDeviceId,
            "originalOperatorId" to draft.operatorId, "warehouseId" to draft.warehouseId,
            "submissionId" to frozen.id, "authorizationId" to draft.authorizationId,
            "receivedPayloadSha256" to frozen.sha256, "declaredUploadSha256" to frozen.sha256,
            "planSha256" to draft.planSha256, "status" to "ABORTED").forEach { (field, expected) -> check(context.get(field) == expected) }
        for (field in listOf("workRound", "freezeDeviceId", "sourceRecoveryEpoch", "authorizationEpoch"))
            check(context.get(field) == body.get(field))
        check(context.get("sourceRecoveryEpoch") == identity.get("serverRecoveryEpoch"))
        actor(receipt.getJSONObject("actor"), input.session)
        sequence(body, draft)
    }

    fun retained(decision: JSONObject, frozen: TemporarySubmission) {
        val rows = decision.getJSONArray("retained"); check(rows.length() > 0)
        val version = TemporaryContinuationProtocol.positive(decision, "retainedManifestVersion")
        check(sha(JSONObject().put("version", version).put("items", rows)) == decision.get("retainedManifestSha256"))
        val scans = JSONObject(frozen.payload).getJSONArray("scans")
        val known = (0 until scans.length()).map { scans.getJSONObject(it) }.filter { it.get("status") == "ACTIVE" }
            .associateBy { it.getString("baleId") }
        val seen = HashSet<String>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i); val bale = TemporaryContinuationProtocol.uuid(row, "baleId")
            check(row.keys().asSequence().toSet() == setOf("baleId", "expectedDispatchCycle", "recordSha256") && seen.add(bale))
            val original = checkNotNull(known[bale]) { "保留包不属于原停止正文" }
            check(TemporaryContinuationProtocol.positive(row, "expectedDispatchCycle") ==
                TemporaryContinuationProtocol.positive(original, "expectedDispatchCycle"))
            check(row.get("recordSha256") == sha(original)) { "保留包原文摘要不匹配" }
        }
    }

    private fun sequence(body: JSONObject, draft: TemporaryDraft) {
        val captures = body.getJSONArray("captures"); val sequences = HashMap<String, Long>()
        val ordered = (0 until captures.length()).map { captures.getJSONObject(it) }
            .sortedBy { TemporaryContinuationProtocol.positive(it, "orderScanSeq") }
        // Higher-round source payload intake is still closed until its retained-baseline contract is implemented.
        check(draft.workRound == 1L && ordered.isNotEmpty())
        ordered.forEachIndexed { index, row ->
            val seq = TemporaryContinuationProtocol.positive(row, "orderScanSeq")
            check(seq == index.toLong() + 1 && row.get("clientOrderId") == draft.clientOrderId)
            check(row.get("originDeviceId") == draft.creatorDeviceId && row.get("authorizationId") == draft.authorizationId)
            check(sequences.put(row.getString("id"), seq) == null)
        }
        check(draft.nextScanSeq == Math.addExact(ordered.size.toLong(), 1))
        check(body.get("captureHighWatermark") == ordered.size.toString())
        val scans = body.getJSONArray("scans"); val ids = HashSet<String>()
        for (i in 0 until scans.length()) {
            val scan = scans.getJSONObject(i); val capture = scan.getString("captureId")
            check(ids.add(capture) && sequences[capture] == TemporaryContinuationProtocol.positive(scan, "orderScanSeq"))
        }
    }

    private fun actor(actor: JSONObject, session: JSONObject) {
        check(actor.get("principalId") == session.get("userId") && actor.get("deviceId") == session.get("deviceId"))
        for (field in listOf("userAuthVersion", "deviceVersion")) check(actor.get(field) == session.get(field))
        val permissions = actor.getJSONObject("permissionSnapshot")
        check(permissions.getJSONArray("requiredPermissions").toString() == "[\"order:resume\"]")
        val effective = permissions.getJSONArray("effectivePermissions")
        check((0 until effective.length()).any { effective.get(it) == "order:resume" })
        val warehouses = permissions.getJSONArray("warehouseIds")
        check((0 until warehouses.length()).any { warehouses.get(it) == session.get("warehouseId") })
    }

    private fun sha(value: JSONObject) = JwtVerifier.sha(SubmissionJson.canonical(value).toByteArray(Charsets.UTF_8))
}
