package com.acrylic.outbound.pda.preparation

import android.content.Context
import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PreparationRepository(private val context: Context, private val db: PdaDatabase, val session: PdaSession) {
    fun local(): List<PreparedOrder> = db.preparations().orders()
    fun pending(): List<ClaimIntent> {
        val auth = session.saved() ?: return emptyList()
        return db.preparations().pending(auth.getString("userId"), auth.getString("deviceId"))
    }

    fun list(path: String, cursorName: String): List<JSONObject> {
        val result = ArrayList<JSONObject>(); var cursor = ""; val seen = HashSet<String>()
        do {
            check(seen.add(cursor)) { "服务器分页游标重复" }
            val page = session.call("$path?limit=200" + if (cursor.isEmpty()) "" else "&$cursorName=${java.net.URLEncoder.encode(cursor, "UTF-8")}")
            val items = page.getJSONArray("items")
            for (index in 0 until items.length()) result.add(items.getJSONObject(index))
            cursor = if (page.isNull("nextCursor")) "" else page.getString("nextCursor")
        } while (cursor.isNotEmpty())
        return result
    }

    fun claim(order: JSONObject, area: String): JSONObject {
        val auth = session.requireSession(); val id = order.getString("id")
        val intent = db.preparations().intent(id) ?: ClaimIntent(id, auth.getString("userId"), auth.getString("deviceId"), UUID.randomUUID().toString(),
            JSONObject().put("expectedVersion", order.getString("rowVersion")).put("areaNote", area).toString(), auth.getString("serverRecoveryEpoch"))
            .also { db.preparations().insertIntent(it) }
        return retryClaim(intent)
    }

    fun retryClaim(intent: ClaimIntent): JSONObject {
        val auth = session.requireSession()
        check(intent.userId == auth.getString("userId") && intent.deviceId == auth.getString("deviceId")) { "原领取属于其他人员，须由原人员恢复" }
        check(intent.recoveryEpoch == auth.getString("serverRecoveryEpoch")) { "恢复代次已变化，请联系主管" }
        // Refresh authentication before sending the persisted write; a lost response keeps the intent intact.
        session.call("pda/devices/me")
        val envelope = session.network().request("pda/orders/${intent.orderId}/assignments", JSONObject(intent.body),
            session.headers(epoch = intent.recoveryEpoch) + ("Idempotency-Key" to intent.key))
        return envelope.getJSONObject("data").also { db.preparations().resolveIntent(intent.orderId, intent.userId, intent.deviceId) }
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
            acknowledge(order)
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
        val auth = session.requireSession()
        JwtVerifier.authorization(JSONObject(order.assignmentJson), auth, JSONObject(order.signingKeyJson), session.now())
        check(db.preparations().count(order.assignmentId) == order.rowCount) { "本机资料不完整" }
        if (order.ackAt == null) "资料已就绪 · 下载确认待重试" else "资料已就绪 · 已确认下载"
    } catch (error: Exception) { error.message ?: "保留资料，需重新认证" }
}
