package com.acrylic.outbound.pda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.network.SessionIdentity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File

data class HandoverCard(val caseId: String, val assignment: String, val title: String,
    val status: String, val action: String, val label: String)
data class HandoverUi(val busy: Boolean = false, val message: String = "输入主管提供的交接编号，或恢复本机交接。",
    val cards: List<HandoverCard> = emptyList(), val hasMore: Boolean = false)

class HandoverViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PdaApplication
    private val mutable = MutableStateFlow(HandoverUi())
    val state = mutable.asStateFlow()
    private var limit = 20

    fun reload() = run("正在读取本机交接资料") { load() }
    fun more() = run("正在读取更多交接") { limit += 20; load() }
    fun stage(caseId: String) = run("正在核验原交接资料") { app.handovers.receive(caseId.trim()); load() }
    fun perform(card: HandoverCard) = run("正在处理交接；完成前保持冻结") {
        when (card.action) {
            "REFRESH" -> app.handovers.receive(card.caseId)
            "IMPORT" -> app.handovers.downloadAndImport(card.caseId, File(app.cacheDir, "handovers")) { text ->
                mutable.update { it.copy(message = text) }
            }
            "ACK" -> app.handovers.acknowledgeDownload(card.caseId)
            "CONTINUE" -> app.work.resumedContinuation(app.handovers.continueWork(card.caseId))
            else -> error("交接状态已变化，请重新读取")
        }
        load()
    }

    private fun load() {
        val auth = JSONObject(app.session.requireSession().toString())
        val ids = app.database.handoverStages().cases(auth.getString("userId"), auth.getString("deviceId"),
            auth.getString("warehouseId"), limit + 1)
        val temporary = app.database.temporaryHandoverStages().cases(auth.getString("userId"), auth.getString("deviceId"),
            auth.getString("warehouseId"), limit + 1)
        val all = (ids + temporary).distinct().sortedDescending()
        val cards = all.take(limit).map { if (it in temporary) temporaryCard(it) else card(it) }
        SessionIdentity.requireSame(auth, app.session.saved())
        mutable.update { it.copy(cards = cards, hasMore = all.size > limit) }
    }

    private fun temporaryCard(id: String): HandoverCard {
        val repository = com.acrylic.outbound.pda.temporary.TemporaryHandoverRepository(app.database,
            com.acrylic.outbound.pda.temporary.TemporarySession(app.session))
        val status = runCatching { repository.read(id); "原临时记录已保存；接收确认和激活尚未完成，保持停止" }
            .getOrElse { "${it.message ?: "临时交接需核对"}；原资料保留" }
        return HandoverCard(id, "", "临时作业交接", status, "REFRESH", "核对接收资格与原资料")
    }

    private fun card(id: String): HandoverCard {
        val stage = checkNotNull(app.database.handoverStages().header(id))
        val manifest = JSONObject(stage.manifestJson)
        val assignment = manifest.optJSONObject("activationReceipt")?.optJSONObject("businessDecision")?.optString("assignmentId").orEmpty()
        val prepared = app.database.preparations().order(assignment)
        val work = app.database.work().state(assignment)
        val submitted = app.database.submissions().currentState(assignment) != null
        val pending = app.database.handoverContinuations().get(id)
        val title = prepared?.planJson?.let { JSONObject(it).getJSONObject("order").getJSONObject("plan").getString("vehicleNo") }
            ?.let { "车号 $it" } ?: "待下载交接订单"
        val (action, label, status) = when {
            app.database.recalls().header(assignment) != null -> Triple("RECALL", "查看召回", "本单已收到召回，停止作业")
            submitted || work == "FROZEN" -> Triple("SUBMISSION", "查看提交与回执", "交接已续作，请核对订单提交结果")
            work == "SCANNING" -> Triple("WORK", "继续作业", "续作凭据已核验，原记录保留")
            work != null && work != "HANDOVER_FROZEN" -> Triple("RECALL", "查看订单状态", "订单已停止，请核对召回状态")
            stage.state == "STAGED" && manifest.optString("status") != "ACTIVATED" -> Triple("REFRESH", "刷新主管处理结果", "等待主管激活，不能开始作业")
            stage.state == "STAGED" -> Triple("IMPORT", "下载并保存交接资料", "原交接资料已核验，尚未导入")
            prepared?.ackAt == null -> Triple("ACK", "重试下载确认", "资料已保存，下载确认未完成；保持冻结")
            pending != null -> Triple("CONTINUE", "恢复原续作请求", "续作结果待核对；原请求保留，保持冻结")
            else -> Triple("CONTINUE", "核验并申请续作", "资料下载已确认；申请成功前保持冻结")
        }
        return HandoverCard(id, assignment, title, status, action, label)
    }

    private fun run(message: String, action: () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = message) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { com.acrylic.outbound.pda.update.UpdateGate.business(action) }
                mutable.update { it.copy(message = "本机资料已保留；请按订单状态继续。") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                withContext(Dispatchers.IO) { runCatching { load() }.onFailure { mutable.update { it.copy(cards = emptyList()) } } }
                mutable.update { it.copy(message = "${error.message ?: "操作未完成"}。原资料与请求保留，请核对后重试。") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
