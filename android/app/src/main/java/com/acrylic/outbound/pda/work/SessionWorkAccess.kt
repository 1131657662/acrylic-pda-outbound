package com.acrylic.outbound.pda.work

import android.content.Context
import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.security.*
import org.json.JSONObject
import java.time.Instant

class SessionWorkAccess(private val context: Context, private val session: PdaSession) : WorkAccess {
    override fun identity(order: PreparedOrder) {
        val current = session.requireSession()
        check(current.getString("userId") == order.operatorId && current.getString("deviceId") == order.deviceId) { "请由原操作员在原设备恢复此订单" }
    }
    override fun authorize(order: PreparedOrder): Instant {
        identity(order)
        val now = session.now()
        JwtVerifier.authorization(JSONObject(order.assignmentJson), session.requireSession(), JSONObject(order.signingKeyJson), now)
        check(context.filesDir.usableSpace >= 200L * 1024 * 1024) { "设备空间不足200MB，停止新增保存并保留资料" }
        return now
    }
    override fun sample() = TrustedTime.sample(context)
}
