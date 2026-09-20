package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.acrylic.outbound.pda.databinding.OrderCardBinding
import org.json.JSONObject

class OrderCards(private val parent: LinearLayout, private val model: PreparationViewModel) {
    fun render(state: PreparationUi) {
        parent.removeAllViews()
        val localItems = state.local
        if (state.section == "LOCAL") localItems.forEach { item ->
            val plan = JSONObject(item.order.planJson).getJSONObject("order")
            val title = "本机 · 车号 ${plan.getJSONObject("plan").getString("vehicleNo") }"
            val renewalNeeded = !item.recalled && item.readiness.contains("已到期")
            val action = when {
                renewalNeeded -> "联网续期"
                item.order.ackAt == null -> "重试下载确认"
                else -> null
            }
            add(title, "订单号 ${plan.getJSONObject("order").getString("orderNo")}\n${item.readiness}\n库存 ${item.order.rowCount} 包 · 计划 ${plan.getJSONObject("plan").getString("plannedTotal")} 包",
                action, !state.busy && state.username.isNotBlank()) {
                    if (renewalNeeded) model.renew(item.order) else model.ack(item.order)
                }
                .work.apply {
                    visibility = View.VISIBLE; isEnabled = !state.busy && state.username.isNotBlank()
                    if (item.recalled) text = "召回核对 / 停止证明"
                    setOnClickListener { context.startActivity(android.content.Intent(context, if (item.recalled) RecallActivity::class.java else WorkActivity::class.java)
                        .putExtra("assignmentId", item.order.assignmentId)) }
                }
        }
        if (state.section == "LOCAL") state.pending.forEach { intent ->
            val rejected = intent.status == "REJECTED"
            add(if (rejected) "领取被拒绝" else "领取结果待核对",
                "订单标识 ${intent.orderId}\n" + (intent.lastError ?: "原请求已保留，网络未知时只能恢复原请求"),
                if (rejected) "刷新订单状态" else "恢复原领取", !state.busy) {
                    if (rejected) model.refresh() else model.retry(intent)
                }
        }
        if (state.section == "LOCAL") state.assigned.filter { assignment -> localItems.none { it.order.orderId == assignment.getString("orderId") } }.forEach { assignment ->
            val own = !assignment.isNull("offlineToken")
            add("本机已领取 · 待准备", "订单标识 ${assignment.getString("orderId")}\n操作员 ${assignment.getString("operatorName")}\n离线期限 ${assignment.getString("expiresAt")}",
                if (own) "下载并校验资料" else "须原操作员登录", own && !state.busy) { model.prepare(assignment) }
        }
        if (state.section == "AVAILABLE") state.available.forEach { order ->
            add("待领取 · 车号 ${order.getString("vehicleNo")}", "订单号 ${order.getString("orderNo")}\n计划 ${order.getString("plannedTotal")} 包",
                "领取订单", !state.busy) { claimDialog(order) }
        }
        if (parent.childCount == 0) add("暂无订单资料", if (state.section == "AVAILABLE") "当前没有可领取订单。" else "本机暂无待处理订单，点击联网刷新查看最新状态。", null, false) {}
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
