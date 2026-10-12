package com.acrylic.outbound.pda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.rescue.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject

internal data class RescueRequestUi(val busy: Boolean = false, val orders: List<RescueOrderChoice> = emptyList(),
    val requests: List<RescueRequestIntent> = emptyList(), val nextCursor: String? = null,
    val message: String = "请选择本人原单；主管核准不等于授予作业权。", val detail: String = "")

class RescueRequestViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RescueRequestRepository.shared(application as PdaApplication)
    private val mutable = MutableStateFlow(RescueRequestUi())
    @Volatile private var operation = 0
    internal val state = mutable.asStateFlow()
    fun load() {
        hide()
        run("正在读取本人原申请") { update ->
            val page = repository.ownOrders()
            val requests = repository.list()
            update { it.copy(orders = page.rows, nextCursor = page.nextCursor, requests = requests,
                message = "本人本机原件已读取；已保存的申请可按原键提交或查询。") }
        }
    }
    fun hide() { operation++; mutable.value = RescueRequestUi() }
    fun nextPage() = run("正在读取下一页本人原单") { update ->
        val page = repository.ownOrders(state.value.nextCursor ?: return@run)
        update { it.copy(orders = page.rows, nextCursor = page.nextCursor) }
    }
    fun prepare(order: Int, rescuerId: String, reason: String) = run("正在保存原申请；尚未发送") { update ->
        val target = state.value.orders.getOrNull(order)?.target ?: error("请先选择本人本机原单")
        val saved = repository.prepare(target, rescuerId, reason)
        val requests = repository.list()
        update { it.copy(requests = requests, detail = summary(saved),
            message = "申请原件已保存，尚未确认服务端接收；请按原键提交或重试。") }
    }
    fun send(index: Int) = run("正在按原键提交；响应丢失仍保留原件") { update ->
        val intent = selected(index); val received = repository.send(intent.key)
        val requests = repository.list()
        update { it.copy(requests = requests, detail = summary(received),
            message = "服务端申请回执已核验并保存；仍须主管核准。") }
    }
    fun refresh(index: Int) = run("正在查询原申请") { update ->
        val received = repository.refresh(selected(index).key)
        val requests = repository.list()
        update { it.copy(requests = requests, detail = summary(received),
            message = "原申请材料已核验保存；这里只显示本次取得的状态。") }
    }
    fun show(index: Int) { if (!state.value.busy) mutable.update { it.copy(detail = state.value.requests.getOrNull(index)?.let(::summary).orEmpty()) } }
    fun showMaterial(index: Int) {
        if (!state.value.busy) mutable.update { it.copy(detail = state.value.requests.getOrNull(index)?.material ?: "尚未取得服务端签名材料，请查询原申请；原件保留。") }
    }
    private fun selected(index: Int) = state.value.requests.getOrNull(index) ?: error("请先选择已保存的原申请")
    private fun summary(intent: RescueRequestIntent): String {
        val doc = intent.document; val receipt = intent.receipt?.let(::JSONObject); val material = intent.material?.let(::JSONObject)
        return "${if (intent.target.source == "FORMAL") "正式" else "临时"}原单 ${intent.target.localOrderId}\n" +
            "指定救援人 ${doc.getString("rescuerId")}\n原因 ${doc.getString("reason")}\n" +
            "原申请键 ${intent.key}\n" + (receipt?.let { "服务端申请编号 ${it.getString("requestId")}\n" } ?: "服务端结果未确认，按原键提交或重试\n") +
            (material?.let { "最后取得状态 ${it.getString("status")}（离线不能得知新撤权）\n" } ?: "尚未查询主管审批材料\n") +
            "原记录保留；申请/核准不代表出库或放行。"
    }
    private fun run(message: String, action: (((RescueRequestUi) -> RescueRequestUi) -> Unit) -> Unit) {
        if (state.value.busy) return
        val currentOperation = operation
        mutable.update { it.copy(busy = true, message = message) }
        viewModelScope.launch(Dispatchers.IO) {
            val update: ((RescueRequestUi) -> RescueRequestUi) -> Unit = { change ->
                mutable.update { if (currentOperation == operation) change(it) else it }
            }
            try { action(update) }
            catch (error: Exception) { update { it.copy(message = "${error.message ?: "本次操作未完成"}；原申请与作业记录保留，服务端结果未确认时按原键重试。") } }
            finally { update { it.copy(busy = false) } }
        }
    }
}
