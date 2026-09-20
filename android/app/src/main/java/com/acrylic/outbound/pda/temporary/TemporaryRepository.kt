package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Callable

class TemporaryRepository(private val db: PdaDatabase, private val connection: TemporaryConnection, private val cache: File) {
    private val access = TemporaryAccess(db, connection)
    fun local(): TemporaryRequest? {
        val auth = connection.current() ?: return null
        return db.temporary().current(auth.getString("userId"), auth.getString("deviceId"), auth.getString("warehouseId"))
    }
    fun prepared(row: TemporaryRequest) = db.temporary().prepared(row.key)
    @Synchronized fun prepareNext(previousKey: String, progress: (String) -> Unit): TemporaryPreparation = UpdateGate.business {
        val expected = access.snapshot()
        begin(expected, previousKey)
        prepare(progress)
    }
    @Synchronized fun prepare(progress: (String) -> Unit): TemporaryPreparation = UpdateGate.business {
        val expected = access.snapshot()
        var request = local() ?: begin(expected)
        access.request(request, expected)
        // A fresh response renews only the trusted time anchor, never the persisted qualification.
        connection.call(TemporaryConnection.Call("pda/devices/me", expected)); access.request(request, expected)
        if (request.receiptJson == null) request = issue(request, expected)
        val row = prepared(request) ?: TemporaryActivation(db, connection).activate(TemporaryActivation.Input(request, expected,
            TemporaryDownload(cache, db, connection).fetch(request, expected, progress)))
        progress("临时资料已完整保存，正在确认下载")
        acknowledge(request, row, expected)
    }
    private fun begin(expected: JSONObject, previousKey: String? = null): TemporaryRequest {
        if (previousKey != null) nextAllowed(previousKey, expected)
        val baseline = connection.call(TemporaryConnection.Call("pda/temporary-preparations/baseline", expected))
        check(baseline.getString("warehouseId") == expected.getString("warehouseId")) { "临时准备基线仓库错误" }
        val body = JSONObject().put("inventorySnapshotId", TemporaryVerifier.uuid(baseline.getString("inventorySnapshotId")))
            .put("mappingRevision", baseline.getString("mappingRevision")).put("policyVersion", baseline.getJSONObject("policy").getString("version"))
        val row = TemporaryRequest(UUID.randomUUID().toString(), expected.getString("userId"), expected.getString("deviceId"),
            expected.getString("warehouseId"), expected.getString("serverRecoveryEpoch"), expected.getString("userAuthVersion"),
            expected.getString("deviceVersion"), body.toString(), expected.getJSONObject("signingKey").toString(), previousKey = previousKey)
        return db.runInTransaction(Callable {
            access.check(expected)
            if (previousKey == null) db.temporary().current(row.userId, row.deviceId, row.warehouseId) ?: row.also { db.temporary().insertRequest(it) }
            else { nextAllowed(previousKey, expected); row.also { db.temporary().insertRequest(it) } }
        })
    }
    private fun nextAllowed(key: String, expected: JSONObject) {
        access.check(expected)
        val previous = checkNotNull(local()) { "没有可接续的原准备" }
        check(previous.key == key) { "准备请求已变化，请恢复当前请求，不能重复新建" }
        check(previous.recoveryEpoch == expected.getString("serverRecoveryEpoch")) { "原资料待恢复对账，不能新建资格" }
        check(db.work().unresolvedCount() == 0 && db.temporaryScans().unresolved() == 0) { "请先处理全机未决输入，再准备新资料" }
        val prepared = checkNotNull(prepared(previous)) { "原准备结果尚未核对，请先恢复原请求" }
        TemporaryAckVerifier.declaration(prepared, previous)
        TemporaryAckVerifier.verify(prepared, previous, JSONObject(checkNotNull(prepared.ackJson) { "请先确认原资料下载，再准备新资料" }))
    }
    private fun issue(request: TemporaryRequest, expected: JSONObject): TemporaryRequest {
        val receipt = connection.call(TemporaryConnection.Call("pda/temporary-preparations", expected, JSONObject(request.bodyJson), request.key))
        val manifest = TemporaryVerifier.manifest(receipt, JSONObject(request.signingKeyJson))
        TemporaryVerifier.identity(manifest, expected); TemporaryVerifier.request(manifest, request)
        return db.runInTransaction(Callable {
            access.request(request, expected)
            db.temporary().received(request.key, receipt.toString())
            checkNotNull(db.temporary().request(request.key)).also {
                val saved = JSONObject(checkNotNull(it.receiptJson))
                check(saved.getString("authorizationId") == receipt.getString("authorizationId") && saved.getString("manifestSha256") == receipt.getString("manifestSha256")) {
                    "原临时准备结果不一致，禁止替换"
                }
            }
        })
    }
    private fun acknowledge(request: TemporaryRequest, row: TemporaryPreparation, expected: JSONObject): TemporaryPreparation {
        access.request(request, expected)
        val manifest = TemporaryAckVerifier.declaration(row, request); TemporaryVerifier.identity(manifest, expected)
        TemporaryParts(db).read(row.authorizationId, manifest)
        if (row.ackJson != null) { TemporaryAckVerifier.verify(row, request, JSONObject(row.ackJson)); return row }
        val ack = connection.call(TemporaryConnection.Call("pda/temporary-preparations/${TemporaryVerifier.uuid(row.authorizationId)}/download-ack",
            expected, JSONObject(row.ackBodyJson), row.ackKey))
        TemporaryAckVerifier.verify(row, request, ack)
        return db.runInTransaction(Callable {
            access.request(request, expected)
            val current = checkNotNull(db.temporary().prepared(request.key))
            check(current.copy(ackJson = row.ackJson) == row) { "本机临时激活记录已变化" }
            db.temporary().acknowledged(row.authorizationId, ack.toString())
            checkNotNull(db.temporary().prepared(request.key)).also { TemporaryAckVerifier.verify(it, request, JSONObject(checkNotNull(it.ackJson))) }
        })
    }
    fun readiness(request: TemporaryRequest): String = try {
        val expected = access.snapshot(); access.request(request, expected)
        val row = prepared(request)
        if (row == null) "临时资料尚未完整保存，请恢复原准备请求" else {
            val manifest = TemporaryAckVerifier.declaration(row, request)
            TemporaryVerifier.identity(manifest, expected); TemporaryVerifier.time(manifest, connection.now())
            TemporaryParts(db).read(row.authorizationId, manifest)
            if (row.ackJson == null) "临时资料已保存 · 下载确认待重试" else {
                TemporaryAckVerifier.verify(row, request, JSONObject(row.ackJson)); "临时资料已保存 · 下载已确认"
            }
        }
    } catch (error: Exception) { error.message ?: "临时资料需核对，原记录已保留" }
}
