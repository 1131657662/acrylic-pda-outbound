package com.acrylic.m71demo.ui

import android.app.AlertDialog
import android.widget.*
import com.acrylic.m71demo.domain.Weight
import com.acrylic.m71demo.fixtures.OrderRequest

class DemoDialogs(private val activity: MainActivity) {
    private val model get() = activity.model
    private val diagnostics = DiagnosticDialogs(activity)

    fun menu() {
        val actions = linkedMapOf<String, () -> Unit>(
            "新建测试订单" to ::newOrder, "订单汇总" to ::summary, "切换历史订单" to { orders() },
            "软件压测（不是实体扫码）" to ::benchmark,
            "停止软件压测" to { model.tools.benchmark.stop() },
            "导出核对文件" to { export(false) }, "冻结测试单并导出" to { export(true) },
            "诊断 / 声音 / 键码 / 故障" to { diagnostics.show() })
        AlertDialog.Builder(activity).setTitle("Demo · 不连接生产库存")
            .setItems(actions.keys.toTypedArray()) { _, index -> actions.values.elementAt(index)() }
            .setNegativeButton("返回", null).show()
    }

    fun auxiliary() {
        val actions = linkedMapOf<String, () -> Unit>(
            "恢复未完成确认 / 存储故障" to { model.engine.enqueue { model.engine.recover() } },
            "确认当前失败提示" to { model.state.value.candidate?.let { model.engine.acknowledge(it.id) } },
            "放弃未确认候选" to { model.state.value.candidate?.let { model.engine.discard(it.id) } },
            "撤销最后一包（确认实物）" to { reason("撤销原因：确认未装车或已卸回") { text ->
                model.engine.enqueue { model.engine.services.correction.voidLast(model.engine.orderId, text) } } },
            "异常备注" to { reason("异常备注，不更改出库数量") { text ->
                model.engine.enqueue { model.engine.services.correction.note(model.engine.orderId, text) } } },
            "更新本地测试授权" to { model.engine.enqueue { model.catalog.renew(model.engine.orderId) } })
        AlertDialog.Builder(activity).setTitle("辅助 F2").setItems(actions.keys.toTypedArray()) { _, index ->
            actions.values.elementAt(index)()
        }.setNegativeButton("返回", null).show()
    }

    private fun newOrder() {
        val vehicle = EditText(activity).apply { hint = "测试车号"; setText("测试车辆") }
        val plans = EditText(activity).apply { hint = "产品:包数,产品:包数"; setText("003:80,571:100") }
        val layout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 0, 20, 0)
            addView(vehicle); addView(plans) }
        AlertDialog.Builder(activity).setTitle("按内置sku建测试单").setView(layout)
            .setMessage("各产品必须填写计划；本机已保存的包持续防重。")
            .setPositiveButton("创建") { _, _ -> model.create(OrderRequest(vehicle.text.toString(), plans.text.toString())) }
            .setNegativeButton("返回", null).show()
    }

    private fun summary() {
        val state = model.state.value
        val text = state.lines.joinToString("\n") { "${it.deliveryCode}：${it.actualCount}/${it.plannedCount} 包，${Weight.kg(it.weightGrams)} kg" }
        AlertDialog.Builder(activity).setTitle("本地汇总 · 非放行凭据")
            .setMessage(text + "\n\n总净重：${Weight.kg(state.order?.weightGrams ?: 0)} kg\n待处理：${state.pending}")
            .setPositiveButton("返回", null).show()
    }

    private fun orders() = model.engine.enqueue {
        val orders = model.engine.store.db.orders().recent()
        activity.runOnUiThread {
            AlertDialog.Builder(activity).setTitle("最近40张测试订单").setItems(
                orders.map { "${it.vehicle} · ${it.actualCount}包 · ${it.state}" }.toTypedArray()) { _, index ->
                model.engine.enqueue { model.engine.select(orders[index].id); model.catalog.select(orders[index].id) }
            }.setNegativeButton("返回", null).show()
        }
    }

    private fun benchmark() {
        AlertDialog.Builder(activity).setTitle("软件链路压测 · 每档10分钟")
            .setMessage("自动生成隔离合成库存、逐条注入完整条码，走校验后自动保存。此结果不能证明真实扫描头达标。")
            .setPositiveButton("选择速率") { _, _ ->
                AlertDialog.Builder(activity).setItems(arrayOf("1包/秒", "2包/秒", "3包/秒", "4包/秒")) { _, index ->
                    model.tools.benchmark.start(index + 1)
                }.show()
            }.setNegativeButton("返回", null).show()
    }

    private fun export(freeze: Boolean) {
        model.export(freeze) { path -> activity.runOnUiThread {
            AlertDialog.Builder(activity).setTitle("已生成测试证据").setMessage("$path\n\n用安装说明中的ADB导出到电脑，再生成Excel。冻结不是云端出库确认。")
                .setPositiveButton("知道了", null).show()
        } }
    }

    private fun reason(title: String, done: (String) -> Unit) {
        val input = EditText(activity).apply { hint = "必填，最多500字"; filters = arrayOf(android.text.InputFilter.LengthFilter(500)) }
        AlertDialog.Builder(activity).setTitle(title).setView(input).setPositiveButton("确认") { _, _ ->
            if (input.text.isNotBlank()) done(input.text.toString()) else activity.notify("必须填写原因")
        }.setNegativeButton("返回", null).show()
    }
}
