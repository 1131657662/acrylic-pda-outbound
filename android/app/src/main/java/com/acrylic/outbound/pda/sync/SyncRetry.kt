package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.update.UpdateGate
import java.io.IOException

data class SyncDecision(val message: String, val delaySeconds: Long? = null, val stopped: Boolean = false)

object SyncRetry {
    fun decide(outcomes: List<SyncOutcome>, attempt: Int): SyncDecision {
        val errors = outcomes.mapNotNull { it.error }
        if (errors.any { it is SessionChanged }) return SyncDecision("登录已变化，下轮将重新核对", 30)
        if (errors.any { !transient(it) }) return SyncDecision("自动同步已暂停，请联网刷新核对；原记录保留", stopped = true)
        if (errors.isNotEmpty()) {
            val backoff = (30L shl attempt.coerceIn(0, 6)).coerceAtMost(1800)
            val retryAfter = errors.filterIsInstance<ApiFailure>().mapNotNull { it.retryAfterSeconds }.maxOrNull() ?: 0
            return SyncDecision("暂未同步完成，将自动重试；原记录保留", maxOf(backoff, retryAfter.coerceAtLeast(0)))
        }
        if (outcomes.any { it.result?.more == true }) return SyncDecision("正在分批同步，原作业资料保留", 30)
        if (outcomes.any { it.result?.supported == false }) return SyncDecision("当前后台尚未支持全部同步，将稍后核对；原记录保留", 21600)
        return SyncDecision("订单、条码和操作记录已同步；原作业授权期限保持")
    }

    private fun transient(error: Exception): Boolean = when (error) {
        is UpdateGate.Busy, is UpdateGate.Installing, is IOException -> true
        is ApiFailure -> error.httpStatus in setOf(408, 429) || (error.httpStatus ?: 0) >= 500 ||
            error.code in setOf("SERVER_RECOVERY_REQUIRED", "SERVER_RECOVERY_EPOCH_MISMATCH")
        else -> false
    }
}
