package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.independent.*
import org.json.JSONObject

internal object IndependentTaskDisplay {
    fun summary(state: IndependentTaskUi): String {
        val task = state.task
        return if (task == null) "计划 ${state.draft?.total() ?: 0} 包 · 尚未采集\n独立来源，待主管对账"
        else "计划 ${task.task.plannedTotal} 包 · 实采 ${task.state.actualCount} 包\n独立来源，待主管对账；不代表出库成功或可放行。"
    }
    fun lines(state: IndependentTaskUi): String {
        state.task?.let { return it.lines.joinToString("\n") { row -> "${row.deliveryCode} · ${row.model} · ${row.name}\n计划 ${row.plannedCount} 包" } }
        val products = state.products.associateBy { it.deliveryCode }
        return state.draft?.quantities?.toSortedMap()?.entries?.joinToString("\n") { (code, count) ->
            val product = products[code]; "$code · ${product?.model.orEmpty()} · ${product?.name.orEmpty()}\n计划 $count 包"
        }.orEmpty().ifEmpty { "尚未添加产品计划。" }
    }
    fun original(state: IndependentTaskUi): String {
        val draft = state.draft ?: return "尚无本机任务；请先启用原sku资料。"
        val base = "本机原请求 ${draft.id}\n原资料编号 ${draft.importId}"
        val task = state.task?.task ?: return base
        return "$base\n原资料摘要 ${task.skuSha256}\n原签名凭据 ${task.credentialId}\n原人员 ${task.operatorId}\n原仓库 ${task.warehouseId}\n创建记录 ${state.task.events.size} 条"
    }
    fun request(row: IndependentTaskRequest): String {
        val command = IndependentTaskCommand.read(JSONObject(row.commandJson))
        return "${command.vehicle} · ${command.total()}包 · ${row.id}"
    }
}
