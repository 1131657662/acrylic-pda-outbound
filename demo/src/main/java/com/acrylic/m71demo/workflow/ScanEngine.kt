package com.acrylic.m71demo.workflow

import com.acrylic.m71demo.device.DemoHardware
import com.acrylic.m71demo.ui.DemoUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.*

@Singleton
class ScanEngine @Inject constructor(val store: DemoStore, val services: WorkflowServices,
    val hardware: DemoHardware, private val processor: CaptureProcessor) {
    private val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue = Channel<() -> Unit>(64)
    private val queued = java.util.concurrent.atomic.AtomicInteger()
    private val mutable = MutableStateFlow(DemoUiState())
    val state: StateFlow<DemoUiState> = mutable.asStateFlow()
    var orderId = ""
        private set

    init {
        com.acrylic.m71demo.device.TransactionTiming.observer = { stage, id, begin, body, commit ->
            hardware.telemetry.event("TRANSACTION_TIMING", mapOf("stage" to stage, "captureId" to id,
                "beginNs" to begin, "bodyNs" to body, "commitNs" to commit))
        }
        scope.launch { for (task in queue) execute(task) }
    }

    fun enqueue(task: () -> Unit): Boolean {
        queued.incrementAndGet()
        if (queue.trySend(task).isSuccess) return true
        queued.decrementAndGet()
        warn("入口已满：暂停并核对，本轮无遗漏测试失败")
        hardware.telemetry.event("QUEUE_OVERFLOW")
        return false
    }

    private fun execute(task: () -> Unit) {
        queued.decrementAndGet()
        try { task(); refresh() }
        catch (e: Exception) {
            warn(e.message ?: "操作失败，请保留数据并检查")
            runCatching { refresh() }
            hardware.telemetry.event("OPERATION_ERROR", mapOf("error" to e.javaClass.simpleName, "message" to e.message))
            hardware.sound.play("ERROR")
        }
    }

    fun select(id: String) {
        if (orderId.isNotEmpty()) check(store.db.captures().pendingCount(orderId) == 0) { "先处理当前订单未决输入" }
        orderId = id
        mutable.update { it.copy(blocked = false, message = "测试单：保存后仍须上传确认，禁止据此放行") }
        recover()
    }

    fun receive(input: IncomingBarcode): Boolean = enqueue {
        if (mutable.value.blocked || input.orderId != orderId) {
            hardware.telemetry.event("INPUT_REFUSED", mapOf("captureId" to input.id, "reason" to "BLOCKED_OR_ORDER_CHANGED"))
            hardware.sound.play("ERROR", input.id)
            return@enqueue
        }
        hardware.telemetry.event("WORKER_RECEIVE", mapOf("captureId" to input.id,
            "queueWaitNs" to store.clock.elapsedNs() - input.receivedElapsedNs))
        processor.receive(input)
        mutable.update { it.copy(message = processor.message) }
        hardware.telemetry.event("QUEUE_OBSERVATION", mapOf("captureId" to input.id, "queued" to queued.get()))
    }

    fun confirm(id: String, pressedAtNs: Long): Boolean = enqueue {
        check(!mutable.value.blocked) { "先恢复存储/处理故障" }
        processor.confirm(id, pressedAtNs)
        mutable.update { it.copy(message = processor.message) }
    }

    fun recover() {
        mutable.update { it.copy(blocked = false) }
        processor.drain(orderId)
        mutable.update { it.copy(message = processor.message) }
    }

    fun acknowledge(id: String) = enqueue { services.correction.acknowledge(id); recover() }
    fun discard(id: String) = enqueue { services.correction.discard(id); recover() }
    fun warn(message: String) { mutable.update { it.copy(message = message, blocked = true) } }

    fun refresh() {
        if (orderId.isEmpty()) return
        val started = store.clock.elapsedNs()
        val failure = store.db.captures().failure(orderId)
        val next = failure ?: store.db.captures().pending(orderId)
        val bale = next?.context?.let { store.db.inventory().bale(it.snapshotId, it.barcode) }
        mutable.update { it.copy(order = store.db.orders().get(orderId),
            lines = store.db.orders().lines(orderId), candidate = next,
            candidateDetail = bale?.let { row -> "${row.deliveryCode} · ${com.acrylic.m71demo.domain.Weight.kg(row.weightGrams)} kg" }.orEmpty(),
            soundError = hardware.sound.lastError,
            recent = store.db.scans().recent(orderId), pending = store.db.captures().pendingCount(orderId),
            queued = queued.get()) }
        hardware.telemetry.event("UI_REFRESH", mapOf("orderId" to orderId, "durationNs" to store.clock.elapsedNs() - started))
    }
}
