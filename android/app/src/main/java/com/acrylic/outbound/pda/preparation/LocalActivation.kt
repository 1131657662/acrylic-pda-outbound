package com.acrylic.outbound.pda.preparation

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.time.Instant

data class VerifiedPackage(val assignment: JSONObject, val receipt: JSONObject, val key: JSONObject,
    val session: JSONObject, val payloads: Map<String, ByteArray>, val now: Instant)

class LocalActivation(private val db: PdaDatabase) {
    fun activate(input: VerifiedPackage): PreparedOrder {
        val claims = JwtVerifier.authorization(input.assignment, input.session, input.key, input.now)
        val manifest = PackageVerifier.manifest(input.receipt, input.assignment, input.key, input.now)
        val parts = manifest.getJSONArray("parts")
        for (index in 0 until parts.length()) {
            val part = parts.getJSONObject(index); val raw = input.payloads.getValue(part.getString("name"))
            check(raw.size == part.getInt("bytes") && JwtVerifier.sha(raw) == part.getString("sha256")) { "激活前摘要不一致" }
        }
        check(JwtVerifier.sha(input.payloads.getValue("plan")) == claims.getString("planSha256"))
        check(JwtVerifier.sha(input.payloads.getValue("inventory")) == claims.getString("inventorySha256"))
        val inventory = JSONArray(text(input.payloads.getValue("inventory"))); val states = JSONArray(text(input.payloads.getValue("availability")))
        val bales = InventoryDecoder.decode(input.assignment.getString("id"), inventory, states)
        for (index in 0 until parts.length()) {
            val part = parts.getJSONObject(index)
            check(part.getInt("rows") == if (part.getString("name") == "plan") 1 else bales.size) { "清单行数不一致" }
        }
        val plan = text(input.payloads.getValue("plan")); InventoryDecoder.validatePlan(JSONObject(plan), claims, bales)
        val order = order(input, manifest, plan, bales.size)
        return persist(order, bales)
    }

    private fun persist(order: PreparedOrder, bales: List<PreparedBale>): PreparedOrder {
        db.runInTransaction {
            val existing = db.preparations().order(order.assignmentId)
            if (existing != null) {
                check(existing.bundleId == order.bundleId && existing.manifestSha256 == order.manifestSha256) { "本机已有不同版本，禁止覆盖" }
                check(db.preparations().count(order.assignmentId) == existing.rowCount) { "本地完整性检查失败，保留资料" }
            } else {
                db.preparations().insertOrder(order)
                bales.chunked(500).forEach { db.preparations().insertBales(it) }
                check(db.preparations().count(order.assignmentId) == order.rowCount)
            }
        }
        return db.preparations().order(order.assignmentId)!!
    }

    private fun order(input: VerifiedPackage, manifest: JSONObject, plan: String, count: Int) = PreparedOrder(
        input.assignment.getString("id"), input.assignment.getString("orderId"), input.assignment.getString("operatorId"),
        input.assignment.getString("deviceId"), manifest.getString("bundleId"), input.receipt.getString("manifestSha256"),
        input.assignment.toString(), manifest.toString(), plan, input.key.toString(), input.receipt.getString("signedManifest"), input.now.toString(), count)

    private fun text(bytes: ByteArray) = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
}
