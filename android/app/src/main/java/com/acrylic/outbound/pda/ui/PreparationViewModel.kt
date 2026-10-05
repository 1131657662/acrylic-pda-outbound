package com.acrylic.outbound.pda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.network.PdaCapabilities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

class PreparationViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PdaApplication
    private val repository get() = app.repository
    private val mutable = MutableStateFlow(PreparationUi())
    private val lastPhaseSuccess = mutableMapOf<String, String>()
    private var loadedSection = "LOCAL"
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
    fun refresh() = run("正在刷新订单与授权") {
        com.acrylic.outbound.pda.sync.SyncScheduler.resume(app)
        refreshData()
    }
    fun selectSection(section: String) {
        mutable.update { it.copy(section = section) }
        if (section in setOf("LOCAL", "HISTORY", "ABNORMAL")) run("正在读取本机记录") { loadLocal() }
    }
    fun loadMore() = run("正在加载下一页订单") {
        val state = mutable.value
        if (state.section == "AVAILABLE" && state.availableHasMore) {
            check(PdaCapabilities.formal(app.session.requireSession())) { "当前人员无正式订单领取权限" }
            val page = repository.page("pda/orders", "cursor", state.availableCursor)
            mutable.update { it.copy(available = it.available + page.items, availableCursor = page.nextCursor,
                availableHasMore = page.nextCursor != null) }
        } else if (state.section in setOf("LOCAL", "HISTORY") && state.localHasMore) {
            val next = localCards(state.section == "HISTORY", state.local.size)
            mutable.update { it.copy(local = it.local + next, localHasMore = next.size == 20) }
        } else if (state.section == "ABNORMAL" && state.abnormalHasMore) {
            val auth = app.session.requireSession()
            val next = app.database.preparations().historyPage(auth.getString("userId"), auth.getString("deviceId"), 20, state.rejectedHistory.size)
            mutable.update { it.copy(rejectedHistory = it.rejectedHistory + next, abnormalHasMore = next.size == 20) }
        } else if (state.section == "LOCAL" && state.assignedHasMore) {
            check(PdaCapabilities.formal(app.session.requireSession())) { "当前人员无正式订单领取权限" }
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
        val assignment = repository.claim(order, area)
        showConfirmedClaim(order.getString("id"), assignment)
        confirmedRefresh("领取已成功")
    }
    fun retry(intent: ClaimIntent) = run("正在恢复原领取请求") {
        showConfirmedClaim(intent.orderId, repository.retryClaim(intent))
        confirmedRefresh("原领取已确认成功")
    }
    fun prepare(assignment: JSONObject) = run("正在准备订单资料") {
        repository.prepare(assignment) { message -> mutable.update { it.copy(message = message) } }
        confirmedRefresh("资料已保存到本机；下载确认状态请查看卡片")
    }
    fun ack(order: PreparedOrder) = run("正在重试下载确认") { repository.acknowledge(order); loadLocal() }
    fun renew(order: PreparedOrder) = run("正在联网续期，原授权和本机记录会保留") { app.renewals.renew(order); loadLocal() }

    private fun showConfirmedClaim(orderId: String, assignment: JSONObject) {
        mutable.update { it.copy(available = it.available.filterNot { row -> row.optString("id") == orderId },
            assigned = listOf(assignment) + it.assigned.filterNot { row -> row.optString("id") == assignment.optString("id") },
            section = "LOCAL", message = "领取已成功；正在核对列表") }
    }
    private fun confirmedRefresh(result: String) {
        try { refreshData(result) }
        catch (error: Exception) {
            if (error is CancellationException) throw error
            mutable.update { it.copy(message = "$result；后续读取失败，原结果和资料已保留：${error.message ?: "请重新刷新"}") }
        }
    }
    private fun refreshData(confirmed: String? = null) {
        val current = app.session.requireSession()
        val results = mutableListOf<String>()
        phase("本机任务", results) { loadLocal(); "已读取" }
        if (PdaCapabilities.formal(current)) {
            phase("本机授权", results) {
                val page = repository.page("pda/assignments", "beforeId")
                mutable.update { it.copy(assigned = page.items, assignedCursor = page.nextCursor,
                    assignedHasMore = page.nextCursor != null) }; "已更新"
            }
            phase("待领取订单", results) {
                val page = repository.page("pda/orders", "cursor")
                mutable.update { state -> state.copy(available = page.items.filterNot { row ->
                    state.assigned.any { it.optString("orderId") == row.optString("id") }
                }, availableCursor = page.nextCursor,
                    availableHasMore = page.nextCursor != null) }; "已更新"
            }
        } else refreshFormal(current)
        if (PdaCapabilities.recovery(current)) phase("召回核对", results) {
            repository.local().filter { it.operatorId == current.getString("userId") && it.deviceId == current.getString("deviceId") }
                .forEach { app.recalls.check(it.assignmentId) }
            loadLocal(); "已核对"
        }
        results += "订单、条码和审计按后台安全策略分批同步；${com.acrylic.outbound.pda.sync.SyncScheduleState(app).message()}"
        mutable.update { it.copy(message = (confirmed?.let { text -> "$text；" } ?: "") + results.joinToString("；")) }
    }
    private fun phase(name: String, results: MutableList<String>, action: () -> String) {
        val start = android.os.SystemClock.elapsedRealtime()
        try {
            val result = action()
            lastPhaseSuccess[name] = java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
            results += "$name：$result"
            android.util.Log.i("PdaOrderFlow", "$name completed in ${android.os.SystemClock.elapsedRealtime() - start}ms")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val previous = lastPhaseSuccess[name]?.let { "；上次成功：$it" }.orEmpty()
            results += "${name}失败，旧资料保留$previous：${error.message ?: "请检查网络后重试"}"
            android.util.Log.w("PdaOrderFlow", "$name failed in ${android.os.SystemClock.elapsedRealtime() - start}ms")
        }
    }
    private fun refreshFormal(current: JSONObject) {
        if (!PdaCapabilities.formal(current)) {
            mutable.update { it.copy(assigned = emptyList(), available = emptyList(), assignedCursor = null,
                availableCursor = null, assignedHasMore = false, availableHasMore = false, section = "LOCAL") }
            return
        }
        val assigned = repository.page("pda/assignments", "beforeId"); val available = repository.page("pda/orders", "cursor")
        mutable.update { it.copy(assigned = assigned.items, available = available.items,
            assignedCursor = assigned.nextCursor, availableCursor = available.nextCursor,
            assignedHasMore = assigned.nextCursor != null, availableHasMore = available.nextCursor != null) }
    }
    private fun loadLocal() {
        val saved = app.session.saved()
        val formal = saved?.let(PdaCapabilities::formal) ?: false
        if (!formal) mutable.update { it.copy(assigned = emptyList(), available = emptyList(), assignedCursor = null,
            availableCursor = null, assignedHasMore = false, availableHasMore = false, section = "LOCAL") }
        val section = mutable.value.section
        val limit = if (section == loadedSection) mutable.value.local.size.coerceAtLeast(20) else 20
        loadedSection = section
        val local = if (section == "ABNORMAL") emptyList() else localCards(section == "HISTORY", 0, limit)
        val history = if (saved == null) emptyList() else app.database.preparations().historyPage(saved.getString("userId"), saved.getString("deviceId"), 20, 0)
        mutable.update { it.copy(bound = app.session.settings.device.isNotBlank(), username = saved?.optString("username") ?: "",
            local = local, localHasMore = section != "ABNORMAL" && local.size == limit,
            rejectedHistory = history, abnormalHasMore = history.size == 20,
            pending = if (saved == null) emptyList() else repository.pending(), deviceLabel = identityLabel(),
            loginRequired = saved == null || saved.has("blockedReason"), formalOrders = formal,
            temporaryVisible = saved != null && (PdaCapabilities.temporary(saved) || app.temporary.local() != null)) }
    }
    private fun localCards(history: Boolean, offset: Int, limit: Int = 20): List<LocalCard> {
        val saved = app.session.saved()
        return repository.localPage(history, offset, limit).map {
            val recalled = app.database.recalls().header(it.assignmentId) != null
            val continuation = app.database.continuations().current(it.assignmentId)
            val current = if (continuation == null) it else it.copy(assignmentJson = continuation.authorizationJson,
                authorizationHistoryJson = continuation.authorizationHistoryJson)
            val readiness = if (app.database.work().state(it.assignmentId) == "HANDOVER_FROZEN") "原资料保留，核验续作前禁止扫码" else repository.readiness(current)
            LocalCard(current, if (recalled) "收到召回 · 停止作业，须核对恢复证据" else readiness, recalled,
                app.database.work().state(it.assignmentId), app.database.submissions().currentState(it.assignmentId),
                saved?.optString("userId") == it.operatorId && saved.optString("deviceId") == it.deviceId)
        }
    }
    private fun identityLabel(): String {
        if (app.session.settings.device.isBlank()) return ""
        val result = app.pairing.saved()?.optJSONObject("result")
        val approval = if (result?.optString("deviceId") == app.session.settings.device) result.optJSONObject("approval") else null
        val name = if (approval == null) "已绑定设备" else "${approval.getString("warehouseName")} · ${approval.getString("name")}"
        return "\n$name"
    }
    private fun run(message: String, action: () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = message) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    com.acrylic.outbound.pda.update.UpdateGate.business(action)
                }
                mutable.update { if (it.message == message) it.copy(message = "操作已完成；资料和本机记录已保留") else it }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                withContext(Dispatchers.IO) { runCatching { loadLocal() } }
                mutable.update { it.copy(message = error.message ?: "操作未完成，资料和占用已保留，请重试") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

}
