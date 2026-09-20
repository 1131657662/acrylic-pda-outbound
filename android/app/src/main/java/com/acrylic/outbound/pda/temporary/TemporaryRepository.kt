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
    private fun begin(expected: JSONObject): TemporaryRequest {
        val baseline = connection.call(TemporaryConnection.Call("pda/temporary-preparations/baseline", expected))
        check(baseline.getString("warehouseId") == expected.getString("warehouseId")) { "临时准备基线仓库错误" }
        val body = JSONObject().put("inventorySnapshotId", TemporaryVerifier.uuid(baseline.getString("inventorySnapshotId")))
            .put("mappingRevision", baseline.getString("mappingRevision")).put("policyVersion", baseline.getJSONObject("policy").getString("version"))
        val row = TemporaryRequest(UUID.randomUUID().toString(), expected.getString("userId"), expected.getString("deviceId"),
            expected.getString("warehouseId"), expected.getString("serverRecoveryEpoch"), expected.getString("userAuthVersion"),
            expected.getString("deviceVersion"), body.toString(), expected.getJSONObject("signingKey").toString())
        return db.runInTransaction(Callable {
            access.check(expected)
            db.temporary().current(row.userId, row.deviceId, row.warehouseId) ?: row.also { db.temporary().insertRequest(it) }
        })
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
