package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.acrylic.outbound.pda.databinding.OrderCardBinding
import com.acrylic.outbound.pda.display.DisplayTime
import org.json.JSONObject

class OrderCards(private val parent: LinearLayout, private val model: PreparationViewModel) {
    private var lastContent = ""
    fun render(state: PreparationUi) {
        val localKey = state.local.joinToString { item ->
            "${item.order.assignmentId}:${item.order.ackAt}:${item.readiness}:${item.recalled}:${item.workState}:${item.submissionState}:${item.originalOperator}"
        }
        val content = listOf(state.section, state.username, state.busy.toString(), localKey,
            state.pending.toString(), state.rejectedHistory.toString(), state.available.joinToString { it.toString() },
            state.assigned.joinToString { it.toString() }).joinToString("|")
        if (lastContent == content) return
        lastContent = content
        parent.removeAllViews()
        if (state.section == "LOCAL" || state.section == "HISTORY") renderLocal(state)
        if (state.section == "LOCAL" || state.section == "ABNORMAL") {
            renderPending(state)
            if (state.section == "LOCAL") renderAssignments(state)
        }
        if (state.section == "ABNORMAL") renderRejections(state)
        if (state.section == "AVAILABLE") renderAvailable(state)
        if (parent.childCount == 0) add("暂无订单资料", when (state.section) {
            "AVAILABLE" -> "当前没有可领取订单；刷新失败时请查看顶部提示。"
            "HISTORY" -> "本机暂无已验签完成的历史订单。"
            "ABNORMAL" -> "本机暂无待恢复或已归档的领取异常。"
            else -> "本机暂无待处理订单，点击联网刷新查看最新状态。"
        }, null, false) {}
    }

    private fun renderLocal(state: PreparationUi) {
        state.local.forEach { item ->
            val plan = JSONObject(item.order.planJson).getJSONObject("order")
            val renewalNeeded = !item.recalled && item.readiness.contains("已到期")
            val action = when {
                item.workState == "HANDOVER_FROZEN" -> null
                item.submissionState == "SYNCED" -> null
                renewalNeeded -> "联网续期"
                item.order.ackAt == null -> "重试下载确认"
                else -> null
            }
            val title = "本机 · 车号 ${plan.getJSONObject("plan").getString("vehicleNo")}"
            val detail = "订单号 ${plan.getJSONObject("order").getString("orderNo")}\n${localStatus(item)} · ${item.readiness}\n库存 ${item.order.rowCount} 包 · 计划 ${plan.getJSONObject("plan").getString("plannedTotal")} 包"
            add(title, detail, action, !state.busy && item.originalOperator) {
                if (renewalNeeded) model.renew(item.order) else model.ack(item.order)
            }.work.apply {
                visibility = View.VISIBLE; isEnabled = !state.busy && item.originalOperator
                text = when {
                    item.workState == "HANDOVER_FROZEN" -> "处理换机交接"
                    item.recalled -> "召回核对 / 停止证明"
                    !item.originalOperator -> "须原操作员登录"
                    item.submissionState != null || item.workState == "FROZEN" -> "查看提交/回执"
                    else -> "继续作业"
                }
                setOnClickListener { context.startActivity(android.content.Intent(context,
                    if (item.workState == "HANDOVER_FROZEN") HandoverActivity::class.java else if (item.recalled) RecallActivity::class.java else if (item.submissionState != null || item.workState == "FROZEN") SubmissionActivity::class.java else WorkActivity::class.java)
                    .putExtra("assignmentId", item.order.assignmentId)) }
            }
        }
    }

    private fun localStatus(item: LocalCard): String = when {
        item.workState == "HANDOVER_FROZEN" -> "换机交接冻结 · 须核验续作"
        item.recalled -> "召回/恢复阻断 · 停止作业"
        item.submissionState == "SYNCED" -> "历史完成 · 已验签回执"
        item.submissionState == "REVIEW_REQUIRED" -> "待主管审核 · 不可放行"
        item.submissionState in setOf("RESULT_UNKNOWN", "RECEIVING") -> "提交结果待核对 · 原批次保留"
        item.submissionState in setOf("FROZEN", "ACTION_REQUIRED", "REGISTERED") -> "待上传/需处理 · 不可放行"
        item.submissionState in setOf("ABORTED", "REJECTED") -> "异常终结 · 须受控处理"
        item.workState == "SCANNING" -> "作业中"
        item.workState == "FROZEN" -> "结单冻结 · 待提交"
        else -> "已准备 · 待作业"
    }

    private fun renderPending(state: PreparationUi) {
        state.pending.forEach { intent ->
            val rejected = intent.status == "REJECTED"
            add(if (rejected) "领取被拒绝" else "领取结果待核对",
                "订单标识 ${intent.orderId}\n" + (intent.lastError ?: "原请求已保留，网络未知时只能恢复原请求"),
                if (rejected) "刷新订单状态" else "恢复原领取", !state.busy) {
                if (rejected) model.refresh() else model.retry(intent)
            }
        }
    }

    private fun renderRejections(state: PreparationUi) {
        state.rejectedHistory.forEach { row ->
            val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(row.archivedAt))
            add("旧领取拒绝 · 已归档", "订单标识 ${row.orderId}\n原拒绝：${row.lastError ?: "未记录原因"}\n归档时间：$date",
                null, false) {}
        }
    }

    private fun renderAssignments(state: PreparationUi) {
        state.assigned.filter { assignment -> state.local.none { it.order.orderId == assignment.getString("orderId") } }.forEach { assignment ->
            val own = !assignment.isNull("offlineToken")
            add("本机已领取 · 待准备 · 车号 ${assignment.optString("vehicleNo", "待核对")}",
                "订单号 ${assignment.optString("orderNo", "待核对")}\n计划 ${assignment.optLong("plannedTotal")} 包 · 操作员 ${assignment.getString("operatorName")}\n离线期限 ${DisplayTime.formatDateTime(assignment.getString("expiresAt"))}",
                if (own) "下载并校验资料" else "须原操作员登录", own && !state.busy) { model.prepare(assignment) }
        }
    }

    private fun renderAvailable(state: PreparationUi) {
        state.available.forEach { order ->
            add("待领取 · 车号 ${order.getString("vehicleNo")}", "订单号 ${order.getString("orderNo")}\n计划 ${order.getString("plannedTotal")} 包",
                "领取订单", !state.busy) { claimDialog(order) }
        }
    }

    private fun add(title: String, detail: String, action: String?, enabled: Boolean, onClick: () -> Unit): OrderCardBinding {
        val binding = OrderCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.title.text = title; binding.details.text = detail
        binding.action.visibility = if (action == null) View.GONE else View.VISIBLE
        binding.action.text = action; binding.action.isEnabled = enabled
        binding.action.setOnClickListener { onClick() }; parent.addView(binding.root)
        return binding
    }

    private fun claimDialog(order: JSONObject) {
        val input = EditText(parent.context).apply { hint = "货堆/区域备注（可留空）"; maxLines = 3 }
        AlertDialog.Builder(parent.context).setTitle("领取 ${order.getString("vehicleNo")}")
            .setMessage("订单号 ${order.getString("orderNo")}\n领取后由本机独占；下载失败或到期仍保留占用。").setView(input)
            .setNegativeButton("返回", null).setPositiveButton("确认领取") { _, _ -> model.claim(order, input.text.toString()) }.show()
    }
}
