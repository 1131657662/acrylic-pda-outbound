package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.sync.RecoveryProtection
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Callable

/** Network retries replay a committed command; only a verified receipt can thaw imported work. */
internal class HandoverContinuationRepository(private val db: PdaDatabase,
    private val connection: HandoverDownloadConnection) {
    fun activate(caseId: String): String {
        val id = UUID.fromString(caseId).toString()
        val expected = JSONObject(checkNotNull(connection.current()) { "请先登录" }.toString())
        val row = prepare(id, expected)
        if (row.continuationId != null) return applied(row, expected)
        val receipt = connection.original(HandoverDownloadConnection.Call("pda/handovers/$id/continuation",
            expected, JSONObject(row.commandJson)))
        return db.runInTransaction(Callable { install(row, receipt, expected) })
    }

    private fun prepare(id: String, expected: JSONObject): LocalHandoverContinuation = db.runInTransaction(Callable {
        guard(expected)
        val old = db.handoverContinuations().get(id)
        if (old?.continuationId != null) { applied(old, expected); return@Callable old }
        val prepared = HandoverDownloadAcknowledgement(db, connection).verified(id, expected)
        check(prepared.ackAt != null) { "请先确认交接资料下载" }
        val stage = checkNotNull(db.handoverStages().get(id))
        val key = old?.commandId ?: UUID.randomUUID().toString()
        val body = HandoverContinuationCommand.body(stage, prepared, key)
        val row = old ?: LocalHandoverContinuation(id, key, body, JwtVerifier.sha(body.toByteArray(Charsets.UTF_8)),
            HandoverContinuationCommand.identity(expected))
        HandoverContinuationCommand.verify(row, stage, prepared, expected)
        if (old == null) db.handoverContinuations().insert(row)
        guard(expected)
        row
    })

    private fun install(row: LocalHandoverContinuation, receipt: JSONObject, expected: JSONObject): String {
        guard(expected)
        val current = checkNotNull(db.handoverContinuations().get(row.caseId))
        if (current.continuationId != null) return applied(current, expected)
        check(current == row) { "原续作命令已变化，请保留资料" }
        val prepared = HandoverDownloadAcknowledgement(db, connection).verified(row.caseId, expected)
        check(prepared.ackAt != null)
        val stage = checkNotNull(db.handoverStages().get(row.caseId))
        HandoverContinuationCommand.verify(row, stage, prepared, checkNotNull(connection.current()))
        val assignment = HandoverContinuationVerifier.verify(stage, JSONObject(prepared.assignmentJson),
            JSONObject(row.commandJson), receipt, checkNotNull(connection.current()), connection.now())
        val root = prepared.assignmentId
        check(db.submissions().forOrder(root) == null && db.continuations().current(root) == null &&
            db.recalls().header(root) == null) { "本机已有提交、续作或召回，不能覆盖" }
        val manifest = JSONObject(stage.manifestJson)
        val round = manifest.getJSONObject("activationReceipt").getJSONObject("businessDecision").getString("workRound").toLong()
        val recovery = JSONObject(stage.recoveryBytes.toString(Charsets.UTF_8))
        val continuation = receipt.getString("continuationId")
        db.continuations().insert(LocalContinuation(continuation, root, assignment.toString(), SubmissionJson.canonical(receipt),
            round, receipt.getLong("nextOrderScanSeq"), recovery.getString("changeRequestId")))
        check(db.work().continueHandover(root) == 1)
        check(db.handoverContinuations().applied(row.caseId, continuation) == 1)
        guard(expected)
        return root
    }

    private fun applied(row: LocalHandoverContinuation, expected: JSONObject): String {
        guard(expected)
        check(row.identityJson == HandoverContinuationCommand.identity(checkNotNull(connection.current())))
        val saved = checkNotNull(db.continuations().get(checkNotNull(row.continuationId)))
        check(db.continuations().current(saved.assignmentId)?.id == saved.id) { "续作已被后续操作替代，请查看原订单" }
        JwtVerifier.authorization(JSONObject(saved.authorizationJson), checkNotNull(connection.current()),
            expected.getJSONObject("signingKey"), connection.now())
        return saved.assignmentId
    }

    private fun guard(expected: JSONObject) {
        RecoveryProtection(db).requireNormal()
        SessionIdentity.requireSame(expected, connection.current())
        check(HandoverContinuationCommand.identity(expected) ==
            HandoverContinuationCommand.identity(checkNotNull(connection.current()))) { "续作期间人员或设备权限已变化" }
    }
}
