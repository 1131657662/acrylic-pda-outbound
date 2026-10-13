package com.acrylic.outbound.pda.ui

import android.app.Application
import androidx.lifecycle.*
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.sync.RecoveryProtection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.UUID

internal data class IndependentTaskUi(val busy: Boolean = false, val draft: IndependentTaskCommand? = null,
    val locked: Boolean = false, val task: IndependentTaskView? = null, val products: List<IndependentSkuBale> = emptyList(),
    val history: List<IndependentTaskRequestHistory> = emptyList(), val message: String = "原人原机的独立任务始终待对账。")

class IndependentTaskViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as PdaApplication
    private val repository = IndependentTaskRepository(app.database, { TrustedTime.sample(app) }) {
        val actor = app.session.requireSession()
        check(actor.getString("deviceId") == app.session.settings.device)
        app.session.publicKey()
        IndependentTaskActor(actor.getString("userId"), actor.getString("deviceId"), actor.getString("warehouseId"))
    }
    private val credentials = IndependentCredentials.shared(app, app.session, RecoveryProtection(app.database))
    private val mutable = MutableStateFlow(IndependentTaskUi())
    internal val state = mutable.asStateFlow()
    init { reload() }
    fun fields(vehicle: String, reason: String) {
        if (!state.value.locked && !state.value.busy) {
            saved["vehicle"] = vehicle; saved["reason"] = reason
            state.value.draft?.copy(vehicle = vehicle, reason = reason)?.let { draft ->
                save(draft); mutable.update { it.copy(draft = draft) }
            }
        }
    }
    fun add(index: Int, count: String) {
        if (state.value.busy || state.value.locked) return
        val product = state.value.products.getOrNull(index) ?: return
        val amount = count.toLongOrNull()
        if (amount == null || amount !in 1..100000) { mutable.update { it.copy(message = "计划须为1至100000包的整数") }; return }
        changePlan(product.deliveryCode, amount)
    }
    fun remove(index: Int) {
        if (state.value.busy || state.value.locked) return
        state.value.products.getOrNull(index)?.let { changePlan(it.deliveryCode, null) }
    }
    private fun changePlan(code: String, amount: Long?) {
        val draft = state.value.draft ?: return
        val quantities = draft.quantities.toMutableMap()
        if (amount == null) quantities.remove(code) else quantities[code] = amount
        if (quantities.values.sum() > 100000 || quantities.size > 200) {
            mutable.update { it.copy(message = "整单最多200个产品、100000包") }; return
        }
        save(draft.copy(quantities = quantities)); mutable.update { it.copy(draft = draft.copy(quantities = quantities), message = "产品计划已更新；保存任务前仍可修改。") }
    }
    fun reload() = run("正在读取原请求与固定计划") {
        val id = saved.get<String>("requestId") ?: repository.latestRequest()?.id
        if (id != null) restore(id) else restoreDraft()
    }
    fun newDraft() = run("正在读取供新任务使用的已启用原资料") { newState() }
    private fun restoreDraft(): IndependentTaskUi {
        val original = saved.get<String>("draftJson") ?: return newState()
        val draft = IndependentTaskCommand.read(JSONObject(original), false)
        val review = IndependentSkuRepository(app.database) { TrustedTime.sample(app) }.review(draft.importId)
        return IndependentTaskUi(draft = draft, products = IndependentTaskCatalog.products(review), history = repository.requests())
    }
    private fun newState(): IndependentTaskUi {
        val (imported, products) = repository.activeProducts()
        val draft = IndependentTaskCommand(UUID.randomUUID().toString(), imported.id, "", "", emptyMap())
        saved.remove<String>("requestId"); saved["vehicle"] = ""; saved["reason"] = ""; save(draft)
        return IndependentTaskUi(draft = draft, products = products, history = repository.requests(), message = "填写车号、原因和产品计划；保存时核验独立口令。")
    }
    private fun restore(id: String): IndependentTaskUi {
        val (command, task) = repository.request(id); saved["requestId"] = id
        saved["vehicle"] = command.vehicle; saved["reason"] = command.reason; save(command)
        return IndependentTaskUi(draft = command, locked = true, task = task, history = repository.requests(),
            message = if (task == null) "原创建请求已保存，任务尚未完成；核验口令后按原计划重试。" else "独立计划已保存，尚未采集；待主管对账。")
    }
    fun create(password: CharArray) {
        val draft = state.value.draft
        if (draft == null) { password.fill('\u0000'); return }
        val command = if (state.value.locked) draft else draft.copy(vehicle = saved.get<String>("vehicle").orEmpty(), reason = saved.get<String>("reason").orEmpty())
        run("正在核验身份并保存原请求、任务和创建审计", password) {
            val grant = credentials.unlock(password); repository.create(command, grant)
            saved["requestId"] = command.id; restore(command.id)
        }
    }
    fun history(older: Boolean) = run("正在读取原任务和未完成请求") {
        val before = if (older) state.value.history.lastOrNull()?.sequence ?: Long.MAX_VALUE else Long.MAX_VALUE
        state.value.copy(history = repository.requests(before), message = "原请求包含已保存任务与未完成创建；选择后打开。")
    }
    fun open(index: Int) {
        val id = state.value.history.getOrNull(index)?.request?.id ?: return
        run("正在读取所选原任务") { restore(id) }
    }
    private fun save(command: IndependentTaskCommand) { saved["draftJson"] = command.json().toString() }
    private fun run(message: String, secret: CharArray? = null, action: () -> IndependentTaskUi) {
        if (state.value.busy) { secret?.fill('\u0000'); return }
        mutable.update { it.copy(busy = true, message = message) }
        viewModelScope.launch {
            try { mutable.value = withContext(Dispatchers.IO) { action().copy(busy = false) } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                val recovered = withContext(Dispatchers.IO) { recoverRequest() }
                mutable.value = recovered.copy(busy = false, message = "${error.message ?: "操作未完成"}。原件保留，请重新读取或按原请求重试。")
            } finally { secret?.fill('\u0000') }
        }
    }
    private fun recoverRequest(): IndependentTaskUi = runCatching {
        val id = state.value.draft?.id
        if (id != null) restore(id) else state.value
    }.getOrDefault(state.value)
}
