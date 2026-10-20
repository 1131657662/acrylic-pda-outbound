package com.acrylic.outbound.pda.preparation

import android.content.Context
import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.network.ApiFailure
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PreparationRepository(private val context: Context, private val db: PdaDatabase, val session: PdaSession) {
    data class RemotePage(val items: List<JSONObject>, val nextCursor: String?)
    fun local(): List<PreparedOrder> = db.preparations().orders()
    fun localPage(history: Boolean, offset: Int, limit: Int = 20): List<PreparedOrder> =
        db.preparations().ordersPage(history, limit, offset)
    fun pending(): List<ClaimIntent> {
        val auth = session.saved() ?: return emptyList()
        return db.preparations().pending(auth.getString("userId"), auth.getString("deviceId"))
    }

    fun page(path: String, cursorName: String, cursor: String? = null, limit: Int = 20): RemotePage {
        require(limit in 20..100) { "分页大小不受支持" }
        val encoded = cursor?.takeIf { it.isNotEmpty() }?.let {
            "&$cursorName=${java.net.URLEncoder.encode(it, "UTF-8")}"
        }.orEmpty()
        val payload = session.call("$path?limit=$limit$encoded")
        val items = payload.getJSONArray("items").let { array ->
            List(array.length()) { index -> array.getJSONObject(index) }
        }
        val next = if (payload.isNull("nextCursor")) null else payload.getString("nextCursor").takeIf { it.isNotEmpty() }
        check(next == null || next != cursor) { "分页未向后推进，请联网刷新" }
        return RemotePage(items, next)
    }

    /** Test and one-off diagnostics helper; screen flows use [page] to keep memory bounded. */
    fun list(path: String, cursorName: String): List<JSONObject> {
        val result = ArrayList<JSONObject>(); var cursor: String? = null; val seen = HashSet<String>()
        do {
            check(seen.add(cursor.orEmpty())) { "服务器分页游标重复" }
            val next = page(path, cursorName, cursor, 100)
            result.addAll(next.items); cursor = next.nextCursor
        } while (cursor != null)
        return result
    }

    fun preview(order: JSONObject): JSONObject {
        val id = order.getString("id"); val auth = session.requireSession()
        val result = session.call("pda/orders/$id/claim-preview")
        check(result.getString("status") == "READY" && result.getString("id") == id &&
            result.getString("rowVersion") == order.getString("rowVersion") &&
            result.getString("orderNo") == order.getString("orderNo") && result.getString("vehicleNo") == order.getString("vehicleNo") &&
            result.getString("warehouseId") == auth.getString("warehouseId")) { "订单已变化，请刷新后重新核对" }
        com.acrylic.outbound.pda.network.SessionIdentity.requireSame(auth, session.requireSession())
        return result
    }

    fun claim(order: JSONObject, area: String): JSONObject {
        val auth = session.requireSession(); val id = order.getString("id")
        val user = auth.getString("userId"); val device = auth.getString("deviceId")
        val previous = db.preparations().intent(id, user, device)
        if (previous?.status != "REJECTED" && previous != null) return retryClaim(previous)
        val preview = session.call("pda/orders/$id/claim-preview")
        check(preview.getString("status") == "READY" && preview.getString("id") == id) { "订单已变化，请刷新核对" }
        val version = preview.getString("rowVersion")
        check(version == order.getString("rowVersion") && preview.getString("orderNo") == order.getString("orderNo") &&
            preview.getString("vehicleNo") == order.getString("vehicleNo")) { "订单已变化，请刷新后重新核对车号与订单号" }
        check(session.requireSession().getString("serverRecoveryEpoch") == auth.getString("serverRecoveryEpoch")) {
            "服务器恢复代次已变化，请重新刷新" }
        if (previous != null) {
            val oldVersion = JSONObject(previous.body).getString("expectedVersion")
            check(version != oldVersion && previous.recoveryEpoch == auth.getString("serverRecoveryEpoch")) {
                "原领取已被拒绝，尚未核实到同一恢复代次的新版本，请联系主管核对" }
        }
        val intent = ClaimIntent(id, user, device, UUID.randomUUID().toString(),
            JSONObject().put("expectedVersion", version).put("areaNote", area).toString(), auth.getString("serverRecoveryEpoch"))
        if (previous == null) db.preparations().insertIntent(intent)
        else db.preparations().replaceRejected(previous, intent, System.currentTimeMillis())
        return retryClaim(intent)
    }

    fun retryClaim(intent: ClaimIntent): JSONObject {
        check(intent.status != "REJECTED") { intent.lastError ?: "原领取已被拒绝，请联系主管核对订单" }
        val auth = session.requireSession()
        check(intent.userId == auth.getString("userId") && intent.deviceId == auth.getString("deviceId")) { "原领取属于其他人员，须由原人员恢复" }
        check(intent.recoveryEpoch == auth.getString("serverRecoveryEpoch")) { "恢复代次已变化，请联系主管" }
        // Refresh authentication before sending the persisted write; a lost response keeps the intent intact.
        session.call("pda/devices/me")
        com.acrylic.outbound.pda.sync.RecoveryProtection(db).requireNormal()
        db.preparations().markIntent(intent.orderId, intent.userId, intent.deviceId, intent.key, "UNKNOWN", null)
        return try {
            val envelope = session.network().request("pda/orders/${intent.orderId}/assignments", JSONObject(intent.body),
                session.headers(epoch = intent.recoveryEpoch) + ("Idempotency-Key" to intent.key))
            envelope.getJSONObject("data").also { db.preparations().resolveIntent(intent.orderId, intent.userId, intent.deviceId) }
        } catch (failure: ApiFailure) {
            if (failure.code in setOf("ORDER_ASSIGNED", "ORDER_VERSION_CONFLICT", "ORDER_STATE_CONFLICT",
                    "ORDER_RECOVERY_REQUIRED", "ORDER_BASELINE_CHANGED")) {
                db.preparations().markIntent(intent.orderId, intent.userId, intent.deviceId, intent.key, "REJECTED", failure.message)
            }
            throw failure
        }
    }

    fun prepare(assignment: JSONObject, progress: (String) -> Unit): PreparedOrder {
        val id = UUID.fromString(assignment.getString("id")).toString(); val epoch = assignment.getString("serverRecoveryEpoch")
        val receipt = session.call("pda/assignments/$id/preparation", JSONObject(), epoch)
        val auth = session.requireSession(); val key = auth.getJSONObject("signingKey")
        JwtVerifier.authorization(assignment, auth, key, session.now())
        val manifest = PackageVerifier.manifest(receipt, assignment, key, session.now())
        val temporary = File(context.cacheDir, "preparation-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val payloads = download(id, manifest, temporary, progress)
            progress("校验完成，正在保存本机资料")
            val order = LocalActivation(db).activate(VerifiedPackage(assignment, receipt, key, session.requireSession(), payloads, session.now()))
            progress("资料已保存，正在确认下载")
            try { acknowledge(order) } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                progress("本机资料已保存，下载确认未完成；请在本机任务重试确认")
            }
            return db.preparations().order(id)!!
        } finally { temporary.deleteRecursively() }
    }

    private fun download(id: String, manifest: JSONObject, directory: File, progress: (String) -> Unit): Map<String, ByteArray> {
        val output = HashMap<String, ByteArray>(); val parts = manifest.getJSONArray("parts")
        for (index in 0 until parts.length()) {
            val part = parts.getJSONObject(index); val name = part.getString("name")
            check(name in setOf("plan", "inventory", "availability"))
            progress("正在下载并校验 ${index + 1}/3")
            val file = File(directory, "$name.gz")
            session.network().download("pda/assignments/$id/content/$name?bundleId=${UUID.fromString(manifest.getString("bundleId"))}", file, session.headers())
            output[name] = PackageVerifier.unpack(file, part)
        }
        return output
    }

    fun acknowledge(order: PreparedOrder) {
        val assignment = JSONObject(order.assignmentJson); val auth = session.requireSession()
        check(order.operatorId == auth.getString("userId") && order.deviceId == auth.getString("deviceId"))
        val body = JSONObject().put("bundleId", order.bundleId).put("manifestSha256", order.manifestSha256)
            .put("assignmentEpoch", assignment.getString("epoch")).put("inventorySnapshotId", assignment.getString("inventorySnapshotId"))
            .put("activatedAt", order.activatedAt)
        val ack = session.call("pda/assignments/${order.assignmentId}/download-ack", body, assignment.getString("serverRecoveryEpoch"))
        check(ack.getString("assignmentId") == order.assignmentId && ack.getString("bundleId") == order.bundleId)
        db.preparations().acknowledged(order.assignmentId, ack.getString("downloadAckAt"))
    }

    fun readiness(order: PreparedOrder): String = try {
        com.acrylic.outbound.pda.sync.SyncWorkGuard(db).requireAllowed(order)
        val auth = session.requireSession()
        JwtVerifier.authorization(JSONObject(order.assignmentJson), auth, JSONObject(order.signingKeyJson), session.now())
        check(db.preparations().count(order.assignmentId) == order.rowCount) { "本机资料不完整" }
        if (order.ackAt == null) "资料已就绪 · 下载确认待重试" else "资料已就绪 · 已确认下载"
    } catch (error: Exception) { error.message ?: "保留资料，需重新认证" }
}
