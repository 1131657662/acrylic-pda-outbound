package com.acrylic.m71demo.ui

import androidx.lifecycle.ViewModel
import com.acrylic.m71demo.export.OrderExporter
import com.acrylic.m71demo.fixtures.*
import com.acrylic.m71demo.workflow.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DemoViewModel @Inject constructor(val engine: ScanEngine, val catalog: OrderCatalog,
    val tools: DemoTools) : ViewModel() {
    val state = engine.state

    init { if (engine.orderId.isEmpty()) engine.enqueue { engine.select(catalog.initialize()) } }

    fun receive(raw: String, source: String, captureId: String? = null) {
        if (engine.orderId.isEmpty() || state.value.blocked || (tools.benchmark.running && source != "TEST_INJECTION")) {
            engine.hardware.telemetry.event("INPUT_REFUSED", mapOf("reason" to "NOT_READY_OR_BENCHMARK", "rawInput" to raw.take(128)))
            engine.hardware.sound.play("ERROR")
            return
        }
        if (raw.length > 128) { engine.warn("条码超过128字符，请停止并核对"); return }
        val clock = engine.store.clock
        val id = captureId?.takeIf { it.length == 36 } ?: java.util.UUID.randomUUID().toString()
        val accepted = engine.receive(IncomingBarcode(id, engine.orderId, raw, source,
            clock.wallMs(), clock.elapsedNs(), clock.bootId()))
        if (accepted && source == "TEST_INJECTION") tools.benchmark.accepted(id)
    }

    fun confirm(captureId: String?) {
        if (tools.benchmark.running) return
        val c = state.value.candidate ?: return
        if (c.id != captureId) return
        when (c.state) {
            "INVALID", "BUSINESS_REJECTED" -> engine.acknowledge(c.id)
            "WAITING_CONFIRM" -> engine.confirm(c.id, engine.store.clock.elapsedNs())
            "STORAGE_ERROR", "CONFIRM_REQUESTED" -> engine.enqueue { engine.recover() }
        }
    }

    fun create(request: OrderRequest) = engine.enqueue {
        check(engine.store.db.captures().pendingCount(engine.orderId) == 0) { "先处理当前未决输入" }
        engine.select(catalog.create(request))
    }

    fun export(freeze: Boolean, done: (String) -> Unit) = engine.enqueue {
        check(!tools.benchmark.running) { "先停止压测并等待队列处理完成" }
        val path = if (freeze) tools.exporter.freeze(engine.orderId) else tools.exporter.export(engine.orderId)
        done(path)
    }

    override fun onCleared() { tools.benchmark.stop() }
}

class DemoTools @Inject constructor(val exporter: OrderExporter, val benchmark: BenchmarkRunner,
    val history: HistoryFixture)
