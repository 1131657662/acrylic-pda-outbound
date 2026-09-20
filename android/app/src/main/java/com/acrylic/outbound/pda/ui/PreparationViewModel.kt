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
    val assigned: List<JSONObject> = emptyList(), val pending: List<ClaimIntent> = emptyList(), val deviceLabel: String = "",
    val section: String = "LOCAL", val availableCursor: String? = null, val assignedCursor: String? = null,
    val availableHasMore: Boolean = false, val assignedHasMore: Boolean = false, val loginRequired: Boolean = false)

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
    fun login(username: String, password: String) = run("正在验证人员与设备", cleanCache = true) {
        app.session.login(username, password); refreshData()
    }
    fun refresh() = run("正在刷新订单与授权", cleanCache = true) {
        com.acrylic.outbound.pda.sync.SyncScheduler.resume(app)
        refreshData()
    }
    fun selectSection(section: String) { mutable.update { it.copy(section = section) } }
    fun loadMore() = run("正在加载下一页订单") {
        val state = mutable.value
        if (state.section == "AVAILABLE" && state.availableHasMore) {
            val page = repository.page("pda/orders", "cursor", state.availableCursor)
            mutable.update { it.copy(available = it.available + page.items, availableCursor = page.nextCursor,
                availableHasMore = page.nextCursor != null) }
        } else if (state.section == "LOCAL" && state.assignedHasMore) {
            val page = repository.page("pda/assignments", "beforeId", state.assignedCursor)
            mutable.update { it.copy(assigned = it.assigned + page.items, assignedCursor = page.nextCursor,
                assignedHasMore = page.nextCursor != null) }
        }
    }
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
    fun renew(order: PreparedOrder) = run("正在联网续期，原授权和本机记录会保留") { app.renewals.renew(order); loadLocal() }

    private fun refreshData() {
        val current = app.session.requireSession()
        val syncMessage = app.sync.refreshOrders() + "；" + app.barcodeSync.refreshBarcodes()
        val restored = app.auditArchives.restore()
        val auditMessage = if (restored) app.auditSync.upload() + "；" + app.auditArchives.archive()
            else "历史审计归档正在分批恢复，本次刷新保留补传屏障"
        repository.local().filter { it.operatorId == current.getString("userId") && it.deviceId == current.getString("deviceId") }
            .forEach { app.recalls.check(it.assignmentId) }
        val assigned = repository.page("pda/assignments", "beforeId")
        val available = repository.page("pda/orders", "cursor")
        mutable.update { it.copy(assigned = assigned.items, available = available.items,
            assignedCursor = assigned.nextCursor, availableCursor = available.nextCursor,
            assignedHasMore = assigned.nextCursor != null, availableHasMore = available.nextCursor != null) }
        loadLocal()
        val archiveMessage = app.archives.refresh()
        mutable.update { it.copy(message = "$syncMessage；$auditMessage；$archiveMessage") }
    }
    private fun loadLocal() {
        val saved = app.session.saved()
        val local = repository.local().map {
            val recalled = app.database.recalls().header(it.assignmentId) != null
            val continuation = app.database.continuations().current(it.assignmentId)
            val current = if (continuation == null) it else it.copy(assignmentJson = continuation.authorizationJson,
                authorizationHistoryJson = continuation.authorizationHistoryJson)
            LocalCard(current, if (recalled) "收到召回 · 停止作业，须核对恢复证据" else repository.readiness(current), recalled)
        }
        mutable.update { it.copy(bound = app.session.settings.device.isNotBlank(), username = saved?.optString("username") ?: "",
            local = local, pending = if (saved == null) emptyList() else repository.pending(), deviceLabel = identityLabel(),
            loginRequired = saved == null || saved.has("blockedReason")) }
    }
    private fun identityLabel(): String {
        if (app.session.settings.device.isBlank()) return ""
        val result = app.pairing.saved()?.optJSONObject("result")
        val approval = if (result?.optString("deviceId") == app.session.settings.device) result.optJSONObject("approval") else null
        val name = if (approval == null) "已绑定设备" else "${approval.getString("warehouseName")} · ${approval.getString("name")}"
        return "\n$name"
    }
    private fun run(message: String, cleanCache: Boolean = false, action: () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = message) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    com.acrylic.outbound.pda.update.UpdateGate.business(action)
                    if (cleanCache) cleanAfterRefresh()
                }
                mutable.update { if (it.message == message) it.copy(message = "操作已完成；资料和本机记录已保留") else it }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                withContext(Dispatchers.IO) { runCatching { loadLocal() } }
                mutable.update { it.copy(message = error.message ?: "操作未完成，资料和占用已保留，请重试") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    private fun cleanAfterRefresh() {
        try { app.retention.clean() }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            mutable.update { it.copy(message = "${it.message}；过期缓存暂未清理，下次联网刷新重试") }
        }
    }
}
