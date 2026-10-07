package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.concurrent.Callable

/** Confirms a consumed continuation ticket without exposing its request to ordinary preparation. */
class TemporaryContinuationDownload(private val store: TemporaryWorkStore) {
    @Synchronized fun acknowledge(ticketId: String, progress: (String) -> Unit = {}): TemporaryPreparation =
        UpdateGate.business {
            val expected = TemporaryAccess(store.db, store.connection).snapshot()
            val link = checkNotNull(store.db.temporaryContinuations().get(ticketId)) { "续作票据消费记录不存在" }
            val request = checkNotNull(store.db.temporary().request(ticketId)) { "续作授权请求不存在" }
            check(request.key == link.ticketId && request.previousKey != null) { "续作授权前驱链不完整" }
            val sourceDraft = checkNotNull(store.db.temporaryDrafts().get(link.sourceClientOrderId))
            val sourcePreparation = checkNotNull(store.db.temporary().byAuthorization(sourceDraft.authorizationId))
            check(request.previousKey == sourcePreparation.requestKey) { "续作授权未绑定原准备请求" }
            val draft = store.load(link.newClientOrderId)
            check(draft.state in setOf("CONTINUATION_BLOCKED", "SCANNING") && draft.workRound > 1) { "续作资料状态不可确认" }
            val access = TemporaryAccess(store.db, store.connection)
            access.request(request, expected)
            val row = checkNotNull(store.db.temporary().prepared(request.key)) { "续作资料尚未完整保存" }
            check(row.requestKey == request.key && row.authorizationId == draft.authorizationId) { "续作资料资格绑定不一致" }
            val manifest = TemporaryAckVerifier.declaration(row, request)
            TemporaryVerifier.identity(manifest, expected)
            TemporaryParts(store.db).read(row.authorizationId, manifest)
            if (row.ackJson != null) {
                TemporaryAckVerifier.verify(row, request, JSONObject(row.ackJson)); return@business row
            }
            progress("正在确认续作资料下载")
            val ack = store.connection.call(TemporaryConnection.Call(
                "pda/temporary-preparations/${TemporaryVerifier.uuid(row.authorizationId)}/download-ack",
                expected, JSONObject(row.ackBodyJson), row.ackKey))
            TemporaryAckVerifier.verify(row, request, ack)
            dbWrite(access, expected, request, row, ack)
        }

    private fun dbWrite(access: TemporaryAccess, expected: JSONObject, request: TemporaryRequest,
        row: TemporaryPreparation, ack: JSONObject): TemporaryPreparation = store.db.runInTransaction(Callable {
        access.request(request, expected)
        val current = checkNotNull(store.db.temporary().prepared(request.key))
        check(current == row) { "续作资料确认记录已变化" }
        check(store.db.temporary().acknowledged(row.authorizationId, ack.toString()) == 1) { "续作资料确认重复或缺失" }
        checkNotNull(store.db.temporary().prepared(request.key)).also {
            TemporaryAckVerifier.verify(it, request, JSONObject(checkNotNull(it.ackJson)))
        }
    })
}
