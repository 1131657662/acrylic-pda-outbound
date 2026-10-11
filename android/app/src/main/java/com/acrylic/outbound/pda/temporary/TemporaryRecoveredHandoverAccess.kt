package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.PdaCapabilities
import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.time.Instant

/** Recovery evidence access preserves the local reconciliation fence and never checks ordinary TEMP admission. */
internal class TemporaryRecoveredHandoverAccess(private val db: PdaDatabase, private val connection: TemporaryConnection,
    private val development: Boolean) {
    fun snapshot(): JSONObject = JSONObject(checkNotNull(connection.current()) { "请先登录" }.toString()).also(::check)
    fun check(expected: JSONObject) {
        check(BuildConfig.DEBUG && development) { "恢复交接接收入口尚未开放" }
        val current = connection.current(); SessionIdentity.requireSame(expected, current)
        checkNotNull(current); check(!current.has("blockedReason")) { current.optString("blockedReason") }
        check(PdaCapabilities.recovery(current)) { "当前身份不能读取恢复资料" }
        check(SubmissionJson.canonical(current.getJSONObject("signingKey")) ==
            SubmissionJson.canonical(expected.getJSONObject("signingKey"))) { "签名核验密钥变化，请保留原资料" }
        for (field in listOf("sessionId", "userAuthVersion", "deviceVersion"))
            check(current.getString(field) == expected.getString(field)) { "接收会话或身份版本变化，请保留原资料" }
        for (field in listOf("userAuthVersion", "deviceVersion", "serverRecoveryEpoch")) TemporaryVerifier.positive(current.getString(field))
        for (field in listOf("sessionId", "localSessionId", "userId", "deviceId", "warehouseId")) TemporaryVerifier.uuid(current.getString(field))
        check(connection.now().isBefore(Instant.parse(current.getString("accessExpiresAt")))) { "接收会话已到期，请保留原资料" }
        val control = checkNotNull(db.sync().recovery()) { "请先取得服务器恢复状态" }
        check(control.mode == "RECONCILING" && control.observedServerEpoch == expected.getString("serverRecoveryEpoch")) {
            "恢复状态与接收会话不一致，请保留原资料"
        }
    }
}
