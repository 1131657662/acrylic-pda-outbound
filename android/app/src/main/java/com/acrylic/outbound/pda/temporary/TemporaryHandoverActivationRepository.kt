package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.concurrent.Callable

class TemporaryHandoverActivationRepository(private val store: TemporaryWorkStore,
    private val development: Boolean = BuildConfig.DEBUG && BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT) {
    private val db = store.db
    private val stages = TemporaryHandoverRepository(db, store.connection)
    private val confirmations = TemporaryHandoverConfirmationRepository(db, store.connection, development)

    fun restore(caseId: String): TemporaryDraft = UpdateGate.business {
        enabled(); val expected = TemporaryAccess(db, store.connection).snapshot()
        confirmations.read(caseId)?.also { check(it.state == "CONFIRMED") } ?: error("请先保存本人接收确认")
        val bytes = store.connection.original(TemporaryConnection.Original("pda/handovers/${TemporaryVerifier.uuid(caseId)}/manifest",
            expected, maximumBytes = 2 * 1024 * 1024))
        val manifest = JSONObject(bytes.toString(Charsets.UTF_8))
        check(manifest.getString("caseId") == caseId && manifest.getString("status") == "ACTIVATED") { "交接尚未激活，请保留原资料" }
        install(caseId, SubmissionJson.canonical(manifest.getJSONObject("activationReceipt")).toByteArray(Charsets.UTF_8))
    }

    @Synchronized fun install(caseId: String, original: ByteArray): TemporaryDraft = UpdateGate.business {
        enabled(); val raw = original.clone(); val expected = TemporaryAccess(db, store.connection).snapshot()
        db.runInTransaction(Callable {
            val stage = stages.read(caseId)
            check(confirmations.read(caseId)?.state == "CONFIRMED") { "本人接收确认尚未保存" }
            val confirmation = checkNotNull(db.temporaryHandoverConfirmations().receipt(caseId))
            val verified = TemporaryHandoverActivationProtocol.verify(raw,
                TemporaryHandoverActivationProtocol.Input(stage, confirmation, expected))
            val saved = db.temporaryHandoverActivations().get(caseId)
            val result = if (saved == null) persist(stage, verified, raw) else replay(saved, verified, raw)
            TemporaryAccess(db, store.connection).check(expected); result
        })
    }

    private fun persist(stage: TemporaryHandoverStage, verified: TemporaryHandoverActivationProtocol.Verified, raw: ByteArray): TemporaryDraft {
        check(verified.decision.getString("retainedCount") == TemporaryHandoverRetained.scans(stage).size.toString())
        store.requireNoOtherInput(verified.decision.getString("newClientOrderId"))
        val draft = TemporaryHandoverInstall(store).persist(stage, verified)
        val row = TemporaryHandoverActivation(stage.caseId, verified.decision.getString("ticketId"), stage.scopeId,
            stage.toWorkRound, draft.clientOrderId, draft.authorizationId, JwtVerifier.sha(raw),
            SubmissionJson.canonical(store.connection.current()!!.getJSONObject("signingKey")), raw)
        db.temporaryHandoverActivations().insert(row); TemporaryHandoverRetained.install(db, stage)
        TemporaryHandoverBaseline(store).read(draft, false)
        store.event(draft, null, "TEMP_HANDOVER_INSTALLED", stage.caseId)
        return draft
    }

    private fun replay(saved: TemporaryHandoverActivation, verified: TemporaryHandoverActivationProtocol.Verified, raw: ByteArray): TemporaryDraft {
        check(saved.receiptSha256 == JwtVerifier.sha(raw) && saved.receiptBytes.contentEquals(raw)) { "激活原回执发生变化，保留原件" }
        val draft = checkNotNull(db.temporaryDrafts().get(saved.newClientOrderId))
        check(draft.clientOrderId == verified.decision.getString("newClientOrderId") &&
            draft.authorizationId == verified.authorization.getString("authorizationId") && draft.workRound == saved.toWorkRound)
        TemporaryHandoverBaseline(store).read(draft)
        return draft
    }

    private fun enabled() { check(BuildConfig.DEBUG && development) { "临时交接开发消费未启用，原资料保留" } }
}
