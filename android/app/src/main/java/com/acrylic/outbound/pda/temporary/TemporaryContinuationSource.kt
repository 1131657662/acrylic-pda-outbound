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
        mapOf("clientOrderId" to input.rootClientOrderId, "creatorDeviceId" to draft.creatorDeviceId,
            "originalOperatorId" to draft.operatorId, "warehouseId" to draft.warehouseId,
            "submissionId" to frozen.id, "authorizationId" to draft.authorizationId,
            "receivedPayloadSha256" to frozen.sha256,
            "planSha256" to draft.planSha256, "status" to "ABORTED").forEach { (field, expected) -> check(context.get(field) == expected) }
        // A one-shot intake has no chunk-upload declaration; its received original digest is still mandatory.
        check(context.has("declaredUploadSha256") && (context.isNull("declaredUploadSha256") ||
            context.get("declaredUploadSha256") == frozen.sha256)) { "原上传声明与冻结正文摘要不符" }
        for (field in listOf("workRound", "freezeDeviceId", "sourceRecoveryEpoch", "authorizationEpoch"))
            check(context.get(field) == body.get(field))
        check(context.get("sourceRecoveryEpoch") == identity.get("serverRecoveryEpoch"))
        actor(receipt.getJSONObject("actor"), input.session)
        sequence(body, input)
    }

    fun retained(decision: JSONObject, frozen: TemporarySubmission) {
        val rows = decision.getJSONArray("retained"); check(rows.length() > 0)
        val version = TemporaryContinuationProtocol.positive(decision, "retainedManifestVersion")
        check(sha(JSONObject().put("version", version).put("items", rows)) == decision.get("retainedManifestSha256"))
        val scans = JSONObject(frozen.payload).getJSONArray("scans")
        val known = (0 until scans.length()).map { scans.getJSONObject(it) }.filter { it.get("status") == "ACTIVE" }
            .associateBy { it.getString("baleId") }
        check(rows.length() == known.size) { "保留清单必须覆盖全部原有效包" }
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

    private fun sequence(body: JSONObject, input: TemporaryContinuationProtocol.Input) {
        val draft = input.draft
        val captures = body.getJSONArray("captures"); val sequences = HashMap<String, Long>()
        val ordered = (0 until captures.length()).map { captures.getJSONObject(it) }
        val prefix = prefix(body, input)
        check(ordered.isNotEmpty())
        ordered.forEachIndexed { index, row ->
            val seq = TemporaryContinuationProtocol.positive(row, "orderScanSeq")
            check(seq == index.toLong() + 1 && row.get("originDeviceId") == draft.creatorDeviceId)
            if (index >= prefix) check(row.get("clientOrderId") == draft.clientOrderId && row.get("authorizationId") == draft.authorizationId)
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

    private fun prefix(body: JSONObject, input: TemporaryContinuationProtocol.Input): Int {
        if (input.draft.workRound == 1L) {
            check(input.predecessor == null && input.rootClientOrderId == input.draft.clientOrderId)
            return 0
        }
        val previous = checkNotNull(input.predecessor) { "缺少已核验的续作前驱原文" }
        check(TemporaryContinuationProtocol.positive(previous, "workRound") + 1 == input.draft.workRound)
        val reference = body.getJSONObject("temporaryContinuation")
        check(reference.get("predecessorSubmissionId") == previous.get("submissionId") && reference.get("predecessorPayloadSha256") == sha(previous))
        for (field in listOf("captures", "scans", "events")) {
            val old = previous.getJSONArray(field); val current = body.getJSONArray(field)
            check(current.length() >= old.length()) { "续作原始前缀缺失" }
            for (i in 0 until old.length()) check(SubmissionJson.canonical(old.getJSONObject(i)) == SubmissionJson.canonical(current.getJSONObject(i))) { "续作原始前缀被改写" }
        }
        val events = body.getJSONArray("events"); val start = previous.getJSONArray("events").length()
        check(events.length() > start)
        for (i in start until events.length()) {
            val event = events.getJSONObject(i)
            check(event.get("authorizationId") == input.draft.authorizationId &&
                SubmissionJson.canonical(event.getJSONObject("source")) == SubmissionJson.canonical(body.getJSONObject("source")))
        }
        return previous.getJSONArray("captures").length()
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
