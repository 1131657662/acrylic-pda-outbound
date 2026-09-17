package com.acrylic.m71demo.fixtures

import com.acrylic.m71demo.workflow.*
import kotlinx.coroutines.*
import javax.inject.Inject

@javax.inject.Singleton
class BenchmarkRunner @Inject constructor(private val engine: ScanEngine, private val fixture: BenchmarkFixture,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val active = java.util.concurrent.atomic.AtomicBoolean()
    private val delivery = BenchmarkDelivery()
    val running: Boolean get() = active.get()

    fun accepted(id: String) = delivery.accepted(id)

    fun start(rate: Int, seconds: Int = 600) {
        require(rate in 1..4 && seconds in 1..1800)
        check(active.compareAndSet(false, true)) { "压测已在运行或准备中" }
        val accepted = engine.enqueue {
            if (!active.get()) return@enqueue
            try {
            check(engine.store.db.captures().pendingCount(engine.orderId) == 0) { "先处理未决输入" }
            val run = fixture.create(rate * seconds)
            engine.select(run.orderId)
            engine.hardware.telemetry.event("BENCHMARK_START", mapOf("orderId" to run.orderId,
                "rate" to rate, "seconds" to seconds, "mode" to "SOFTWARE_PRIVATE_BROADCAST_AUTO_SAVE_NOT_PHYSICAL",
                "broadcastAction" to com.acrylic.m71demo.device.ScannerReceiver.SOFTWARE_ACTION))
            job = scope.launch { runFeed(run, rate) }
            } catch (e: Exception) { active.set(false); throw e }
        }
        if (!accepted) active.set(false)
    }

    private suspend fun runFeed(run: BenchmarkRun, rate: Int) {
        try { feed(run, rate) }
        catch (e: TimeoutCancellationException) { failed(run, e) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            failed(run, e)
        } finally { active.set(false) }
    }

    private fun failed(run: BenchmarkRun, error: Exception) {
        engine.hardware.telemetry.event("BENCHMARK_FAILED", mapOf("orderId" to run.orderId,
            "error" to error.javaClass.simpleName, "message" to error.message))
        engine.warn("自动测试已中止，请保留证据检查；本轮不计通过")
    }

    private suspend fun feed(run: BenchmarkRun, rate: Int) {
        val start = engine.store.clock.elapsedNs()
        repeat(run.count) { index ->
            if (!active.get()) return
            check(!engine.state.value.blocked && engine.state.value.candidate?.state !in setOf("INVALID", "BUSINESS_REJECTED")) { "压测遇到错误，停止输入并核对" }
            val due = start + index * 1_000_000_000L / rate
            delay(((due - engine.store.clock.elapsedNs()) / 1_000_000).coerceAtLeast(0))
            val b = BenchmarkFixture.bale(run.snapshot, run.startSerial + index, index)
            val input = IncomingBarcode(java.util.UUID.randomUUID().toString(), run.orderId, b.barcode,
                "TEST_INJECTION", engine.store.clock.wallMs(), engine.store.clock.elapsedNs(), engine.store.clock.bootId())
            delivery.sendAndAwait(input.id) { context.sendBroadcast(android.content.Intent(com.acrylic.m71demo.device.ScannerReceiver.SOFTWARE_ACTION)
                .setPackage(context.packageName)
                .putExtra(com.acrylic.m71demo.device.ScannerReceiver.BARCODE_KEY, input.raw)
                .putExtra(com.acrylic.m71demo.device.ScannerReceiver.TEST_ID_KEY, input.id)) }
        }
        engine.enqueue { engine.hardware.telemetry.event("BENCHMARK_INPUT_ENDED", mapOf("orderId" to run.orderId)) }
    }

    fun stop() {
        if (!active.getAndSet(false)) return
        job?.cancel()
        engine.hardware.telemetry.event("BENCHMARK_STOP", mapOf("orderId" to engine.orderId))
    }
}
