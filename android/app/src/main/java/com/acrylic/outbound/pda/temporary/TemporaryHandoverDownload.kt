package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.concurrent.Callable

/** Persists the recipient's own TEMP download acknowledgement after handover activation. */
class TemporaryHandoverDownload(private val store: TemporaryWorkStore,
    private val development: Boolean = BuildConfig.DEBUG && BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT) {
    @Synchronized
    fun acknowledge(id: String): TemporaryPreparation = UpdateGate.business {
        check(BuildConfig.DEBUG && development) { "临时交接下载确认开发入口未启用" }
        val draft = store.load(id)
        check(draft.state in setOf("HANDOVER_BLOCKED", "SCANNING")) { "交接资料状态不可确认" }
        TemporaryHandoverBaseline(store).read(draft, false)
        acknowledgeDraft(draft)
    }

    private fun acknowledgeDraft(draft: TemporaryDraft): TemporaryPreparation {
        val activation = checkNotNull(store.db.temporaryHandoverActivations().get(
            checkNotNull(store.db.temporaryHandoverActivations().caseForTarget(draft.clientOrderId))))
        val request = checkNotNull(store.db.temporary().request(activation.ticketId))
        check(request.previousKey == null) { "交接新资格不得复用续作前驱票据" }
        check(store.db.temporaryContinuations().get(request.key) == null) { "交接新资格不得使用续作票据" }
        val current = checkNotNull(store.db.temporaryDrafts().get(draft.clientOrderId))
        check(current == draft && current.authorizationId == activation.authorizationId)
        val row = checkNotNull(store.db.temporary().prepared(request.key))
        check(row.authorizationId == draft.authorizationId && row.requestKey == request.key)
        val expected = TemporaryAccess(store.db, store.connection).snapshot()
        TemporaryAccess(store.db, store.connection).request(request, expected)
        val manifest = TemporaryAckVerifier.declaration(row, request)
        TemporaryVerifier.identity(manifest, expected)
        TemporaryParts(store.db).read(row.authorizationId, manifest)
        row.ackJson?.let {
            TemporaryAckVerifier.verify(row, request, JSONObject(it))
            return row
        }
        val ack = store.connection.call(TemporaryConnection.Call(
            "pda/temporary-preparations/${TemporaryVerifier.uuid(row.authorizationId)}/download-ack",
            expected, JSONObject(row.ackBodyJson), row.ackKey))
        TemporaryAckVerifier.verify(row, request, ack)
        return dbWrite(expected, request, row, ack)
    }

    private fun dbWrite(expected: JSONObject, request: TemporaryRequest,
        row: TemporaryPreparation, ack: JSONObject): TemporaryPreparation = store.db.runInTransaction(Callable {
        TemporaryAccess(store.db, store.connection).request(request, expected)
        val current = checkNotNull(store.db.temporary().prepared(request.key))
        check(current == row) { "交接下载确认记录已变化" }
        check(store.db.temporary().acknowledged(row.authorizationId, ack.toString()) == 1) { "交接下载确认重复或缺失" }
        checkNotNull(store.db.temporary().prepared(request.key)).also {
            TemporaryAckVerifier.verify(it, request, JSONObject(checkNotNull(it.ackJson)))
        }
    })
}
