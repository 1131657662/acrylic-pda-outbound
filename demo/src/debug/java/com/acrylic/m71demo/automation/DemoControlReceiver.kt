package com.acrylic.m71demo.automation

import android.content.*
import android.util.AtomicFile
import com.acrylic.m71demo.device.ScannerReceiver
import com.acrylic.m71demo.fixtures.*
import com.acrylic.m71demo.ui.DemoTools
import com.acrylic.m71demo.workflow.ScanEngine
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

/** Debug-only same-UID automation. No production endpoint or hardware acceptance claim. */
@AndroidEntryPoint
class DemoControlReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: ScanEngine
    @Inject lateinit var catalog: OrderCatalog
    @Inject lateinit var fixture: BenchmarkFixture
    @Inject lateinit var tools: DemoTools

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("requestId") ?: return
        if (runCatching { java.util.UUID.fromString(id).toString() == id }.getOrDefault(false).not()) return
        val output = File(engine.hardware.telemetry.directory, "automation/$id.json")
        if (output.exists()) return
        engine.enqueue {
            val result = runCatching { execute(context, intent) }.fold(
                { JSONObject().put("ok", true).put("result", it) },
                { JSONObject().put("ok", false).put("error", it.message).put("type", it.javaClass.simpleName) })
            output.parentFile!!.mkdirs()
            val file = AtomicFile(output)
            val stream = file.startWrite()
            try { stream.write(result.put("requestId", id).toString().toByteArray()); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
        }
    }

    private fun execute(context: Context, intent: Intent): JSONObject {
        val operation = intent.getStringExtra("op") ?: error("Missing operation")
        if (operation == "STATUS") return status()
        check(!tools.benchmark.running) { "Benchmark running" }
        if (operation in setOf("INPUT", "CONFIRM", "ACK", "VOID", "NOTE", "FREEZE", "PLAN_LIMIT"))
            check(engine.store.db.orders().get(engine.orderId)?.source == "SYNTHETIC_TEST") { "Synthetic orders only" }
        when (operation) {
            "CREATE" -> {
                val run = fixture.create(intent.getIntExtra("count", 4))
                engine.select(run.orderId); catalog.select(run.orderId)
                return JSONObject().put("orderId", run.orderId).put("snapshot", run.snapshot)
                    .put("startSerial", run.startSerial).put("count", run.count)
            }
            "SELECT" -> { val id = requireNotNull(intent.getStringExtra("orderId")); engine.select(id); catalog.select(id) }
            "BENCHMARK" -> tools.benchmark.start(intent.getIntExtra("rate", 4), intent.getIntExtra("seconds", 600))
            "INPUT" -> input(context, intent)
            "RECOVER" -> engine.recover()
            "CONFIRM" -> engine.confirm(requireNotNull(intent.getStringExtra("captureId")), engine.store.clock.elapsedNs())
            "ACK" -> { engine.services.correction.acknowledge(requireNotNull(intent.getStringExtra("captureId"))); engine.recover() }
            "VOID" -> engine.services.correction.voidLast(engine.orderId, "AUTOMATION: simulated physical verification only")
            "NOTE" -> engine.services.correction.note(engine.orderId, "AUTOMATION: exception note fixture")
            "EXPORT" -> return JSONObject().put("path", tools.exporter.export(engine.orderId))
            "FREEZE" -> { setFault(intent); return JSONObject().put("path", tools.exporter.freeze(engine.orderId)) }
            "HISTORY" -> history()
            "PLAN_LIMIT" -> limitPlans(intent.getIntExtra("limit", 3))
            else -> error("Unknown operation")
        }
        return status()
    }

    private fun limitPlans(limit: Int) = engine.store.db.runInTransaction {
        val lines = engine.store.db.orders().lines(engine.orderId)
        require(limit > 0 && lines.all { it.actualCount == 0 && limit <= it.plannedCount })
        check(engine.store.db.captures().count(engine.orderId) == 0)
        lines.forEach { engine.store.db.orders().updateLine(it.copy(plannedCount = limit)) }
        engine.store.audit(engine.orderId, null,
            com.acrylic.m71demo.workflow.AuditDetail("AUTOMATION_PLAN_LIMIT", "Synthetic over-plan fixture"))
    }

    private fun input(context: Context, intent: Intent) {
        val order = requireNotNull(engine.store.db.orders().get(engine.orderId))
        check(order.source == "SYNTHETIC_TEST") { "Automation input only for synthetic orders" }
        val raw = requireNotNull(intent.getStringExtra("raw"))
        require(raw.length <= 128)
        setFault(intent)
        val id = intent.getStringExtra("captureId") ?: java.util.UUID.randomUUID().toString()
        if (intent.getBooleanExtra("manual", false)) {
            engine.receive(com.acrylic.m71demo.workflow.IncomingBarcode(id, order.id, raw, "MANUAL",
                engine.store.clock.wallMs(), engine.store.clock.elapsedNs(), engine.store.clock.bootId()))
        } else context.sendBroadcast(Intent(ScannerReceiver.SOFTWARE_ACTION).setPackage(context.packageName)
            .putExtra(ScannerReceiver.BARCODE_KEY, raw).putExtra(ScannerReceiver.TEST_ID_KEY, id))
    }

    private fun setFault(intent: Intent) {
        val point = intent.getStringExtra("fault") ?: "NONE"
        require(point in setOf("NONE", "BEFORE_CAPTURE", "AFTER_CAPTURE", "AFTER_CONFIRM", "BEFORE_SAVE",
            "DURING_SAVE", "AFTER_SAVE", "AFTER_FEEDBACK", "BEFORE_FREEZE_REGISTER", "AFTER_FREEZE_REGISTER"))
        val mode = intent.getStringExtra("faultMode") ?: "STORAGE_ERROR"
        require(mode in setOf("STORAGE_ERROR", "KILL_PROCESS"))
        engine.hardware.fault.mode = mode; engine.hardware.fault.point = point
    }

    private fun history() {
        check(engine.store.db.captures().pendingCount(engine.orderId) == 0)
        check(engine.hardware.telemetry.directory.usableSpace >= 2_000_000_000L)
        tools.history.seed { count ->
            engine.warn("正在生成合成历史：$count / 360000，暂停扫码")
            engine.hardware.telemetry.event("HISTORY_PROGRESS", mapOf("count" to count))
        }
        engine.recover()
    }

    private fun status(): JSONObject {
        val order = engine.store.db.orders().get(engine.orderId)
        val next = engine.store.db.captures().failure(engine.orderId) ?: engine.store.db.captures().pending(engine.orderId)
        return JSONObject().put("orderId", engine.orderId).put("saved", order?.actualCount)
            .put("weightGrams", order?.weightGrams).put("orderState", order?.state)
            .put("pending", engine.store.db.captures().pendingCount(engine.orderId))
            .put("captureId", next?.id).put("captureState", next?.state).put("error", next?.error)
            .put("benchmarkRunning", tools.benchmark.running).put("blocked", engine.state.value.blocked)
            .put("wizardPhysicalAcceptance", false)
    }
}
