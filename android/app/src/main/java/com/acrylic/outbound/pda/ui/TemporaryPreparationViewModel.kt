package com.acrylic.outbound.pda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.network.PdaCapabilities
import com.acrylic.outbound.pda.temporary.TemporaryVerifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TemporaryUi(val busy: Boolean = false, val status: String = "正在读取本机资料", val details: String = "",
    val action: String = "联网准备临时资料", val allowed: Boolean = false, val requestKey: String? = null)

class TemporaryPreparationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PdaApplication
    private val mutable = MutableStateFlow(TemporaryUi())
    val state = mutable.asStateFlow()
    fun reload() = run { load() }
    fun prepare() = run {
        app.temporary.prepare { message -> mutable.update { it.copy(status = message) } }
        load()
    }
    fun prepareNext(key: String) = run {
        app.temporary.prepareNext(key) { message -> mutable.update { it.copy(status = message) } }
        load()
    }
    private fun load() {
        val auth = app.session.saved(); val request = app.temporary.local()
        val allowed = auth != null && !auth.has("blockedReason") && PdaCapabilities.temporary(auth)
        val details = if (request?.receiptJson == null) "资格有效期固定为48小时，联网恢复不会延长。" else {
            val manifest = TemporaryVerifier.manifest(JSONObject(request.receiptJson), JSONObject(request.signingKeyJson))
            val row = app.temporary.prepared(request)
            "有效至：${displayTime(manifest.getString("expiresAt"))}" +
                if (row == null) "\n资料尚未完整保存" else "\n库存 ${row.inventoryRows} 包 · 状态 ${row.availabilityRows} 包"
        }
        val status = if (request != null) app.temporary.readiness(request)
            else if (allowed) "尚未准备临时作业资料" else "当前账号或设备未开启临时作业，请联系主管核对后重新登录"
        mutable.update { it.copy(status = status, details = details, allowed = allowed, requestKey = request?.key,
            action = if (request == null) "联网准备临时资料" else "联网恢复原准备请求") }
    }
    private fun displayTime(value: String) = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault()).format(Instant.parse(value))
    private fun run(action: () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                withContext(Dispatchers.IO) { runCatching { load() } }
                mutable.update { it.copy(status = "${error.message ?: "准备未完成"}；原请求和资料已保留") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
