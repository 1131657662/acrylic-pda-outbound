package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.network.ApiFailure
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Describes only this read request; it never infers an order result or grants offline work. */
internal data class ConnectionDiagnosis(val summary: String, val details: String = "") {
    companion object {
        fun signedOut() = ConnectionDiagnosis("尚未核对后台：请先返回人员登录。原作业资料保留。")
        fun success() = ConnectionDiagnosis("后台连接正常，当前登录已核对。订单结果与作业资格仍按原单核验。")

        fun failure(error: Exception): ConnectionDiagnosis {
            val causes = generateSequence<Throwable>(error) { it.cause }.take(10).toList()
            val api = causes.filterIsInstance<ApiFailure>().firstOrNull()
            val summary = if (api != null) apiSummary(api) else transportSummary(causes)
            val details = causes.joinToString("\n") { cause ->
                val code = if (cause is ApiFailure) " · ${cause.code} · HTTP ${cause.httpStatus ?: "未知"}" else ""
                "${cause.javaClass.simpleName}$code\n${cause.message.orEmpty()}"
            }
            return ConnectionDiagnosis("$summary\n原作业记录保留；此次检查不改变订单结果。", details)
        }

        private fun apiSummary(error: ApiFailure): String = when {
            error.code in setOf("AUTH_EXPIRED", "DEVICE_DISABLED") || error.httpStatus == 401 ->
                "后台已响应，当前身份不可用。请返回人员登录或联系管理员核对设备。"
            error.code == "PERMISSION_DENIED" || error.httpStatus == 403 ->
                "后台已响应，当前身份无权核对设备。请联系管理员核对仓库及权限。"
            error.code.startsWith("SERVER_RECOVERY") ->
                "后台已响应，服务正在恢复。保持原作业停止，等待主管完成恢复核对。"
            error.httpStatus == 429 -> "后台已响应，检查过于频繁。请稍后重试。"
            error.httpStatus in 500..599 -> "后台已响应，服务暂不能完成检查。请稍后重试或联系管理员。"
            else -> "后台已响应，本次检查未通过。请展开原始详情并联系管理员核对。"
        }

        private fun transportSummary(causes: List<Throwable>): String = when {
            causes.any { it is SSLException } -> "安全连接未通过。请核对服务地址、证书和设备时间，勿绕过证书校验。"
            causes.any { it is UnknownHostException } -> "无法解析后台地址。请检查 Wi-Fi、DNS 和服务地址后重试。"
            causes.any { it is SocketTimeoutException } -> "后台检查超时，尚不能确认后台可达。请检查网络或稍后重试。"
            causes.any { it is ConnectException } -> "未能连接后台。请检查网络、服务地址及后台是否运行。"
            causes.any { it is IOException } -> "连接或传输中断，尚不能确认后台可达。请检查网络后重试。"
            else -> "本次检查未完成，原因尚待核对。请展开原始详情，保留资料并联系管理员。"
        }
    }
}
