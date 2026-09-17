package com.acrylic.m71demo.ui

import android.app.AlertDialog
import android.os.Build
import com.acrylic.m71demo.device.ScannerReceiver

class DiagnosticDialogs(private val activity: MainActivity) {
    private val engine get() = activity.model.engine

    fun show() {
        val actions = linkedMapOf<String, () -> Unit>(
            "设备和数据库信息" to { info() },
            "全部按键验证向导（可独立完成）" to { activity.openKeyWizard() },
            "切换应用音效" to { engine.hardware.sound.enabled = !engine.hardware.sound.enabled
                activity.notify("应用音效：${engine.hardware.sound.enabled}；厂家识别音另设") },
            "试听保存音" to { engine.hardware.sound.play("SAVED") },
            "试听错误音" to { engine.hardware.sound.play("ERROR") },
            "单次故障注入" to ::fault,
            "历史库：36万条 / 90天" to ::history,
            "模拟后台状态 / 恢复代次" to ::serverFixture,
            "模拟当前包周期改变" to { engine.enqueue {
                val barcode = engine.state.value.candidate?.context?.barcode ?: error("先扫描一包，暂不回车")
                engine.store.db.inventory().simulateCycleChange(barcode)
                activity.notify("测试周期已改变；原候选回车必须拒绝") } })
        AlertDialog.Builder(activity).setTitle("诊断 / 设置")
            .setItems(actions.keys.toTypedArray()) { _, index -> actions.values.elementAt(index)() }
            .setNegativeButton("返回", null).show()
    }

    private fun history() {
        AlertDialog.Builder(activity).setTitle("生成容量夹具（合成历史）")
            .setMessage("新增36万条扫码、108万条审计。需至少2GB可用空间，期间暂停输入；可能耗时较长。不会删除原有记录。中断后保留已完成的整单。")
            .setPositiveButton("生成") { _, _ -> engine.enqueue {
                check(!activity.model.tools.benchmark.running && engine.store.db.captures().pendingCount(engine.orderId) == 0)
                check(engine.hardware.telemetry.directory.usableSpace >= 2_000_000_000L) { "空间不足2GB" }
                engine.warn("正在生成合成历史，请勿扫码或切单")
                activity.model.tools.history.seed { count ->
                    engine.warn("正在生成合成历史：$count / 360000，暂停扫码")
                    engine.hardware.telemetry.event("HISTORY_PROGRESS", mapOf("count" to count))
                }
                engine.recover()
                activity.notify("历史夹具已生成，可开始热库压测")
            } }.setNegativeButton("返回", null).show()
    }

    private fun serverFixture() {
        val states = arrayOf("FROZEN_TEST_ONLY", "PENDING_REVIEW_TEST", "CONFIRMED_TEST_FIXTURE", "CHANGE_RECOVERY_EPOCH")
        AlertDialog.Builder(activity).setTitle("本地响应夹具，全部不可放行").setItems(states) { _, index -> engine.enqueue {
            val order = requireNotNull(engine.store.db.orders().get(engine.orderId))
            val next = if (index == 3) order.copy(recoveryEpoch = java.util.UUID.randomUUID().toString())
                else order.copy(state = states[index])
            engine.store.db.runInTransaction {
                engine.store.db.orders().update(next)
                engine.store.audit(order.id, null, com.acrylic.m71demo.workflow.AuditDetail("SIMULATED_SERVER_STATE", states[index]))
            }
            activity.notify("仅模拟状态，未上传且没有云端放行凭据")
        } }.setNegativeButton("返回", null).show()
    }

    private fun info() = engine.enqueue {
        val settings = com.acrylic.m71demo.data.DatabaseDiagnostics.writer(engine.store.db)
        val metrics = activity.resources.displayMetrics
        val text = "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n" +
            "${metrics.widthPixels}×${metrics.heightPixels} px，density=${metrics.density}\n" +
            "SQLite写连接：${settings.journalMode} / synchronous=${settings.synchronous}（FULL=2）\n诊断丢弃：${engine.hardware.telemetry.dropped.get()}\n" +
            "设备ID：${engine.store.device().id}\n按键：${activity.keys.profileStatus}\n广播：${ScannerReceiver.ACTION}\n数据键：${ScannerReceiver.BARCODE_KEY}"
        engine.hardware.telemetry.event("DEVICE_INFO", mapOf("text" to text, "fingerprint" to Build.FINGERPRINT))
        activity.runOnUiThread { AlertDialog.Builder(activity).setTitle("诊断信息").setMessage(text).setPositiveButton("返回", null).show() }
    }

    private fun fault() {
        val points = arrayOf("NONE", "BEFORE_CAPTURE", "AFTER_CAPTURE", "AFTER_CONFIRM", "BEFORE_SAVE", "DURING_SAVE", "AFTER_SAVE", "AFTER_FEEDBACK", "BEFORE_FREEZE_REGISTER", "AFTER_FREEZE_REGISTER")
        AlertDialog.Builder(activity).setTitle("选择下一次故障点").setItems(points) { _, index ->
            engine.hardware.fault.point = points[index]
            AlertDialog.Builder(activity).setTitle("故障类型（测试数据）")
                .setItems(arrayOf("模拟存储错误", "终止本应用进程（不是真断电）")) { _, mode ->
                    engine.hardware.fault.mode = if (mode == 0) "STORAGE_ERROR" else "KILL_PROCESS"
                    activity.notify("已设一次故障：${points[index]}")
                }.show()
        }.setNegativeButton("返回", null).show()
    }
}
