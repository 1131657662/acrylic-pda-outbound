package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import org.json.JSONObject
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Callable

class TemporaryActivation(private val db: PdaDatabase, private val connection: TemporaryConnection) {
    data class Input(val request: TemporaryRequest, val expected: JSONObject, val payloads: Map<String, ByteArray>)
    fun activate(input: Input): TemporaryPreparation {
        val receipt = JSONObject(checkNotNull(input.request.receiptJson))
        val manifest = TemporaryHandoverAuthorization.declaration(input.request)
        TemporaryVerifier.request(manifest, input.request); TemporaryVerifier.identity(manifest, input.expected)
        TemporaryPayloads.verify(manifest, input.payloads)
        return db.runInTransaction(Callable {
            val access = TemporaryAccess(db, connection); access.request(input.request, input.expected)
            check(db.temporary().request(input.request.key) == input.request) { "原准备请求已变化" }
            TemporaryVerifier.time(manifest, connection.now())
            val existing = db.temporary().prepared(input.request.key)
            if (existing != null) {
                check(existing.authorizationId == receipt.getString("authorizationId") && existing.manifestSha256 == receipt.getString("manifestSha256"))
                TemporaryParts(db).read(existing.authorizationId, manifest); existing
            } else persist(input, receipt, manifest).also {
                access.request(input.request, input.expected); TemporaryVerifier.time(manifest, connection.now())
            }
        })
    }
    private fun persist(input: Input, receipt: JSONObject, manifest: JSONObject): TemporaryPreparation {
        val at = connection.now().truncatedTo(ChronoUnit.MILLIS).toString()
        val body = JSONObject().put("bundleId", receipt.getString("bundleId")).put("manifestSha256", receipt.getString("manifestSha256"))
            .put("authorizationEpoch", manifest.getJSONObject("identity").getString("authorizationEpoch")).put("activatedAt", at)
        val parts = TemporaryVerifier.descriptors(manifest).associateBy { it.getString("name") }
        val row = TemporaryPreparation(receipt.getString("authorizationId"), input.request.key, receipt.getString("bundleId"),
            receipt.getString("manifestSha256"), at, parts.getValue("inventory").getInt("rows"), parts.getValue("availability").getInt("rows"),
            UUID.randomUUID().toString(), body.toString())
        db.temporary().insertPreparation(row); TemporaryParts(db).insert(row.authorizationId, input.payloads)
        return row
    }
}
