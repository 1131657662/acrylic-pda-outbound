package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.concurrent.Callable

/** Saves explicit intent before network; the signed original and completion progress commit atomically. */
class TemporaryHandoverConfirmationRepository(private val db: PdaDatabase, private val connection: TemporaryConnection,
    private val development: Boolean = BuildConfig.DEBUG && BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT) {
    private val access = TemporaryAccess(db, connection)
    private val stages = TemporaryHandoverRepository(db, connection)

    @Synchronized fun queue(caseId: String, physicalConfirmed: Boolean): TemporaryHandoverConfirmation = UpdateGate.business {
        enabled(); check(physicalConfirmed) { "请先明确核对实物与原交接资料" }
        val expected = access.snapshot()
        db.runInTransaction(Callable {
            val stage = stages.read(caseId); val dao = db.temporaryHandoverConfirmations()
            var pending = dao.get(stage.caseId)
            if (pending == null) {
                pending = TemporaryHandoverConfirmationProtocol.create(stage, expected); dao.insert(pending)
            }
            TemporaryHandoverConfirmationProtocol.request(pending, stage, expected)
            if (pending.state == "CONFIRMED") verifySaved(pending, stage, expected)
            else check(dao.progress(caseId, "PENDING", "", true) == 1)
            access.check(expected); checkNotNull(dao.get(caseId))
        })
    }

    @Synchronized fun resume(caseId: String): TemporaryHandoverConfirmation = UpdateGate.business {
        enabled(); val dao = db.temporaryHandoverConfirmations()
        val pending = checkNotNull(dao.get(caseId)) { "请先明确确认实物与原资料" }
        try {
            val expected = access.snapshot(); val stage = stages.read(caseId)
            val command = TemporaryHandoverConfirmationProtocol.request(pending, stage, expected)
            if (pending.state == "CONFIRMED") { verifySaved(pending, stage, expected); return@business pending }
            if (!pending.autoRetry) return@business pending
            val bytes = connection.original(TemporaryConnection.Original("pda/handovers/$caseId/temporary-confirmation", expected,
                body = SubmissionJson.canonical(command).toByteArray(Charsets.UTF_8), maximumBytes = 8 * 1024 * 1024))
            val input = TemporaryHandoverConfirmationProtocol.Response(bytes, expected)
            TemporaryHandoverConfirmationProtocol.receipt(pending, stage, input)
            persist(pending, input)
        } catch (error: Exception) {
            val retry = error is java.io.IOException || error is ApiFailure &&
                error.code in setOf("INTERNAL_ERROR", "LOGIN_RATE_LIMIT", "SERVER_RECOVERY_REQUIRED")
            if (dao.get(caseId)?.state != "CONFIRMED") dao.progress(caseId, if (retry) "PENDING" else "ACTION_REQUIRED", error.message.orEmpty().take(500), retry)
            throw error
        }
        checkNotNull(dao.get(caseId))
    }

    fun read(caseId: String): TemporaryHandoverConfirmation? = db.runInTransaction(Callable {
        val expected = access.snapshot(); val stage = stages.read(caseId)
        db.temporaryHandoverConfirmations().get(caseId)?.also {
            TemporaryHandoverConfirmationProtocol.request(it, stage, expected)
            if (it.state == "CONFIRMED") verifySaved(it, stage, expected)
            access.check(expected)
        }
    })

    private fun persist(pending: TemporaryHandoverConfirmation, input: TemporaryHandoverConfirmationProtocol.Response) {
        db.runInTransaction {
            access.check(input.expected); val stage = stages.read(pending.caseId); val dao = db.temporaryHandoverConfirmations()
            val current = checkNotNull(dao.get(pending.caseId))
            check(current.boundCommandJson == pending.boundCommandJson && current.commandSha256 == pending.commandSha256)
            val receipt = TemporaryHandoverConfirmationProtocol.receipt(current, stage, input)
            val saved = dao.receipt(pending.caseId)
            if (saved == null) dao.receipt(receipt) else {
                check(saved.receiptBytes.contentEquals(receipt.receiptBytes) && saved.receiptSha256 == receipt.receiptSha256)
                verifySaved(current, stage, input.expected)
            }
            check(dao.progress(pending.caseId, "CONFIRMED", "", false) == 1); access.check(input.expected)
        }
    }

    private fun verifySaved(row: TemporaryHandoverConfirmation, stage: TemporaryHandoverStage, expected: JSONObject) {
        val receipt = checkNotNull(db.temporaryHandoverConfirmations().receipt(row.caseId)) { "原确认回执缺失，保留原请求" }
        check(JwtVerifier.sha(receipt.receiptBytes) == receipt.receiptSha256)
        TemporaryHandoverConfirmationProtocol.receipt(row, stage,
            TemporaryHandoverConfirmationProtocol.Response(receipt.receiptBytes, expected, JSONObject(receipt.signingKeyJson)))
    }

    private fun enabled() { check(development && BuildConfig.DEBUG) { "临时交接开发确认未启用，原资料保留" } }
}
