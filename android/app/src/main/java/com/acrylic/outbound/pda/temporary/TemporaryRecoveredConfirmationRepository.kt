package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.concurrent.Callable

/** Saves recovery intent before POST; receipt and completion become durable in one Room transaction. */
class TemporaryRecoveredConfirmationRepository(private val db: PdaDatabase, private val connection: TemporaryConnection,
    development: Boolean = BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT) {
    private val access = TemporaryRecoveredHandoverAccess(db, connection, development)
    private val stages = TemporaryRecoveredHandoverRepository(db, connection, development)
    @Synchronized fun queue(caseId: String, physicalConfirmed: Boolean): TemporaryRecoveredConfirmation = UpdateGate.business {
        check(physicalConfirmed) { "请先明确核对实物与原恢复资料" }; val expected = access.snapshot()
        db.runInTransaction(Callable {
            val stage = stages.read(caseId); val manifest = manifest(stage); val dao = db.temporaryRecoveredConfirmations()
            val row = dao.get(caseId) ?: TemporaryRecoveredConfirmationCommand.create(stage, manifest).also(dao::insert)
            val current = TemporaryRecoveredConfirmationReceiptVerifier.Context(row, stage, manifest)
            TemporaryRecoveredConfirmationCommand.require(row, stage, manifest)
            if (row.state == "CONFIRMED") saved(current, expected) else {
                check(dao.receipt(caseId) == null) { "恢复确认存在未完成的原回执，请保留资料" }
                check(dao.progress(caseId, "PENDING", "", true) == 1)
            }
            access.check(expected); checkNotNull(dao.get(caseId))
        })
    }
    @Synchronized fun resume(caseId: String): TemporaryRecoveredConfirmation = UpdateGate.business {
        val dao = db.temporaryRecoveredConfirmations(); checkNotNull(dao.get(caseId)) { "请先明确确认实物与原资料" }
        try {
            val expected = access.snapshot(); val context = context(caseId)
            val command = TemporaryRecoveredConfirmationCommand.require(context.row, context.stage, context.manifest)
            if (context.row.state == "CONFIRMED") { saved(context, expected); return@business context.row }
            check(dao.receipt(caseId) == null) { "恢复确认存在未完成的原回执，请保留资料" }
            if (!context.row.autoRetry) return@business context.row
            val bytes = connection.original(TemporaryConnection.Original("pda/handovers/$caseId/recovered-confirmation", expected,
                body = command, maximumBytes = 8 * 1024 * 1024))
            persist(context.row, TemporaryRecoveredConfirmationReceiptVerifier.Response(bytes, expected, connection.now()))
        } catch (error: Exception) {
            val retry = error is java.io.IOException || error is ApiFailure &&
                error.code in setOf("INTERNAL_ERROR", "LOGIN_RATE_LIMIT", "CONTROL_COORDINATOR_BUSY")
            dao.progress(caseId, if (retry) "PENDING" else "ACTION_REQUIRED", error.message.orEmpty().take(500), retry)
            throw error
        }
        checkNotNull(dao.get(caseId))
    }
    fun read(caseId: String): TemporaryRecoveredConfirmation? = db.runInTransaction(Callable {
        val expected = access.snapshot(); stages.read(caseId)
        db.temporaryRecoveredConfirmations().get(caseId)?.also {
            val context = context(caseId); TemporaryRecoveredConfirmationCommand.require(it, context.stage, context.manifest)
            if (it.state == "CONFIRMED") saved(context, expected); access.check(expected)
        }
    })
    private fun persist(pending: TemporaryRecoveredConfirmation, input: TemporaryRecoveredConfirmationReceiptVerifier.Response) {
        db.runInTransaction {
            access.check(input.expected); val current = context(pending.caseId); val dao = db.temporaryRecoveredConfirmations()
            check(current.row.commandJson == pending.commandJson && current.row.commandSha256 == pending.commandSha256)
            val receipt = TemporaryRecoveredConfirmationReceiptVerifier.verify(current, input)
            if (current.row.state == "CONFIRMED") {
                check(checkNotNull(dao.receipt(pending.caseId)).receiptBytes.contentEquals(receipt.receiptBytes)) { "原恢复确认回执变化，请保留资料" }
                saved(current, input.expected)
            } else {
                check(dao.receipt(pending.caseId) == null) { "恢复确认存在未完成的原回执，请保留资料" }
                dao.receipt(receipt); check(dao.progress(pending.caseId, "CONFIRMED", "", false) == 1)
            }
            access.check(input.expected)
        }
    }
    private fun saved(context: TemporaryRecoveredConfirmationReceiptVerifier.Context, expected: JSONObject) {
        val receipt = checkNotNull(db.temporaryRecoveredConfirmations().receipt(context.row.caseId)) { "原恢复确认回执缺失，请保留请求" }
        check(JwtVerifier.sha(receipt.receiptBytes) == receipt.receiptSha256 && receipt.signingKeyJson == context.stage.signingKeyJson)
        TemporaryRecoveredConfirmationReceiptVerifier.verify(context, TemporaryRecoveredConfirmationReceiptVerifier.Response(
            receipt.receiptBytes, expected, connection.now(), JSONObject(receipt.signingKeyJson)))
    }
    private fun context(caseId: String): TemporaryRecoveredConfirmationReceiptVerifier.Context {
        val stage = stages.read(caseId)
        val row = checkNotNull(db.temporaryRecoveredConfirmations().get(caseId)) { "请先明确确认实物与原资料" }
        return TemporaryRecoveredConfirmationReceiptVerifier.Context(row, stage, manifest(stage))
    }
    private fun manifest(stage: TemporaryRecoveredHandoverStage) = JSONObject(db.temporaryRecoveredHandoverChunks()
        .read(stage.caseId, "MANIFEST", stage.manifestLength).toString(Charsets.UTF_8))
}
