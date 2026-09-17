package com.acrylic.outbound.pda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

data class LocalCard(val order: PreparedOrder, val readiness: String, val recalled: Boolean = false)
data class PreparationUi(val busy: Boolean = false, val message: String = "请先绑定设备并登录", val bound: Boolean = false,
    val username: String = "", val local: List<LocalCard> = emptyList(), val available: List<JSONObject> = emptyList(),
    val assigned: List<JSONObject> = emptyList(), val pending: List<ClaimIntent> = emptyList(), val deviceLabel: String = "")

class PreparationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PdaApplication
    private val repository get() = app.repository
    private val mutable = MutableStateFlow(PreparationUi())
    val state: StateFlow<PreparationUi> = mutable.asStateFlow()

    init { run("正在恢复本机资料") { loadLocal() } }

    fun bind(server: String, device: String) = run("正在绑定设备") {
        check(app.pairing.saved() == null) { "本机已有配对申请，请通过短码登记页恢复，不得手工覆盖身份" }
        app.session.settings.bind(server, device, app.session.publicKey()); loadLocal()
    }
    fun reloadLocal() = run("正在读取本机身份与资料") { loadLocal() }
    suspend fun publicKey(): String = withContext(Dispatchers.IO) { app.session.publicKey() }
    fun login(username: String, password: String) = run("正在验证人员与设备") {
        app.session.login(username, password); refreshData()
    }
    fun refresh() = run("正在刷新订单与授权") { refreshData() }
    fun logout() = run("正在退出，本机资料会保留") {
        try { app.session.logout() } finally {
            mutable.update { it.copy(available = emptyList(), assigned = emptyList(), pending = emptyList()) }; loadLocal()
        }
    }
    fun claim(order: JSONObject, area: String) = run("正在领取，结果未知时请重试原请求") {
        repository.claim(order, area); refreshData()
    }
    fun retry(intent: ClaimIntent) = run("正在恢复原领取请求") { repository.retryClaim(intent); refreshData() }
    fun prepare(assignment: JSONObject) = run("正在准备订单资料") {
        repository.prepare(assignment) { message -> mutable.update { it.copy(message = message) } }; refreshData()
    }
    fun ack(order: PreparedOrder) = run("正在重试下载确认") { repository.acknowledge(order); loadLocal() }

    private fun refreshData() {
        val current = app.session.requireSession()
        repository.local().filter { it.operatorId == current.getString("userId") && it.deviceId == current.getString("deviceId") }
            .forEach { app.recalls.check(it.assignmentId) }
        val assigned = repository.list("pda/assignments", "beforeId")
        val available = repository.list("pda/orders", "cursor")
        mutable.update { it.copy(assigned = assigned, available = available) }; loadLocal()
    }
    private fun loadLocal() {
        val saved = runCatching { app.session.saved() }.getOrNull()
        val local = repository.local().map {
            val recalled = app.database.recalls().header(it.assignmentId) != null
            val continuation = app.database.continuations().current(it.assignmentId)
            val current = if (continuation == null) it else it.copy(assignmentJson = continuation.authorizationJson)
            LocalCard(current, if (recalled) "收到召回 · 停止作业，须核对恢复证据" else repository.readiness(current), recalled)
        }
        mutable.update { it.copy(bound = app.session.settings.device.isNotBlank(), username = saved?.optString("username") ?: "",
            local = local, pending = if (saved == null) emptyList() else repository.pending(), deviceLabel = identityLabel()) }
    }
    private fun identityLabel(): String {
        if (app.session.settings.device.isBlank()) return ""
        val result = app.pairing.saved()?.optJSONObject("result")
        val approval = if (result?.optString("deviceId") == app.session.settings.device) result.optJSONObject("approval") else null
        val name = if (approval == null) "已绑定设备" else "${approval.getString("warehouseName")} · ${approval.getString("name")}"
        return "\n$name\n${app.session.settings.server}"
    }
    private fun run(message: String, action: () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = message) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                mutable.update { it.copy(message = "资料状态已更新；请在结单页上传并核对云端回执，待审核不能放行") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                withContext(Dispatchers.IO) { runCatching { loadLocal() } }
                mutable.update { it.copy(message = error.message ?: "操作未完成，资料和占用已保留，请重试") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
