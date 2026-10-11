package com.acrylic.outbound.pda.work

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import com.acrylic.outbound.pda.update.UpdateGate

data class WorkUi(val order: WorkOrder? = null, val lines: List<WorkLine> = emptyList(),
    val candidate: WorkCapture? = null, val recent: List<WorkScan> = emptyList(), val pending: Int = 0,
    val queued: Int = 0, val blocked: String = "", val message: String = "正在恢复作业资料", val ready: Boolean = false,
    val dispositions: Map<String, String> = emptyMap())

/** Application-owned single writer. Pausing the page stops automatic processing, not durable evidence. */
class WorkEngine(private val store: WorkStore, private val sound: (String) -> Unit) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue = Channel<() -> Unit>(64)
    private val queued = AtomicInteger()
    private val mutable = MutableStateFlow(WorkUi())
    val state: StateFlow<WorkUi> = mutable.asStateFlow()
    @Volatile private var foreground = false
    @Volatile private var fault = ""
    @Volatile private var assignment = ""
    @Volatile private var requested = ""
    private var scanLease: AutoCloseable? = null
    val isScanning: Boolean get() = foreground
    private val recalls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val processor = WorkProcessor(store) { type, _ ->
        if (foreground) sound(type)
        mutable.update { it.copy(message = if (type == "SAVED") "已保存到本机，尚未上传" else "本包未保存，请确认处理结果") }
    }

    init {
        scope.launch { for (task in queue) execute(task) }
        scope.launch { while (isActive) { delay(1000); if (foreground && queued.get() == 0) runCatching { publish() }.onFailure { stop(it.message.orEmpty()) } } }
    }

    @Synchronized fun resume(id: String) {
        if (scanLease == null) scanLease = try { UpdateGate.enterBusiness() } catch (error: UpdateGate.Installing) {
            mutable.update { it.copy(ready = false, message = error.message.orEmpty()) }; return
        }
        requested = id
        if (id != assignment) mutable.value = WorkUi()
        else mutable.update { it.copy(ready = false) }
        foreground = true
        enqueue {
            store.open(id); assignment = id
            mutable.update { it.copy(message = "扫码通过自动保存；手输校验后确认") }
            if (fault.isEmpty()) processor.drain(id) { active(id) }
        }
    }
    @Synchronized fun pause() { foreground = false; mutable.update { it.copy(ready = false) }; scanLease?.close(); scanLease = null }
    @Synchronized fun pauseForRecall(id: String) {
        recalls.add(id)
        if (requested != id) return
        foreground = false
        mutable.update { it.copy(ready = false) }
        scanLease?.close()
        scanLease = null
    }
    fun resumedContinuation(id: String) { recalls.remove(id); if (requested == id) fault = "" }
    fun receive(raw: String, source: String): Boolean {
        if (!foreground || assignment.isEmpty() || assignment != requested || fault.isNotEmpty() || !mutable.value.ready) {
            sound("ERROR"); return false
        }
        val input = WorkInput(assignment, raw, source, store.access.sample())
        return enqueue {
            processor.reception.receive(input)
            processor.drain(input.assignmentId) { active(input.assignmentId) }
        }
    }
    fun confirm(id: String) = enqueue {
        check(active(assignment)) { "请先恢复作业" }
        processor.saving.confirm(id); processor.drain(assignment) { active(assignment) }
    }
    fun acknowledge(id: String) = enqueue {
        processor.corrections.acknowledge(id)
        if (fault.isEmpty()) processor.drain(assignment) { active(assignment) }
    }
    fun discard(id: String, reason: String) = enqueue {
        processor.corrections.discard(id, reason)
        if (fault.isEmpty()) processor.drain(assignment) { active(assignment) }
    }
    fun voidLast(id: String, reason: String) = enqueue { processor.corrections.voidLast(assignment, id, reason) }
    fun adjust(order: String, capture: String, reason: String): Deferred<Unit> {
        val result = CompletableDeferred<Unit>()
        if (!enqueue {
            try { check(!foreground) { "请先暂停扫码再调整" }; processor.corrections.voidSelected(order, capture, reason); result.complete(Unit) }
            catch (error: Exception) { result.completeExceptionally(error) }
        }) result.completeExceptionally(IllegalStateException("调整未接收，请保留原记录并重试"))
        return result
    }
    fun recover() = enqueue {
        store.open(requested); assignment = requested
        store.verifyTotals(assignment); store.access.authorize(store.prepared(assignment)); fault = ""
        processor.drain(assignment) { active(assignment) }
    }
    fun stop(message: String) {
        fault = message.ifBlank { "存储或输入故障，请停扫核对；未确认落盘的输入须重新核对" }
        mutable.update { it.copy(blocked = fault, ready = false) }; sound("ERROR")
    }
    fun close() { pause(); scope.cancel(); dispatcher.close() }
    private fun active(id: String) = foreground && requested == id && fault.isEmpty() && !recalls.contains(id)

    private fun enqueue(action: () -> Unit): Boolean {
        val lease = try { UpdateGate.enterBusiness() } catch (error: UpdateGate.Installing) {
            mutable.update { it.copy(ready = false, message = error.message.orEmpty()) }; return false
        }
        queued.incrementAndGet()
        mutable.update { it.copy(queued = queued.get()) }
        if (queue.trySend { try { action() } finally { lease.close() } }.isSuccess) return true
        lease.close()
        queued.decrementAndGet(); mutable.update { it.copy(queued = queued.get()) }
        stop("入口已满，请停扫核对；本次未接收，不保证已持久化")
        return false
    }
    private fun execute(action: () -> Unit) {
        try { action() }
        catch (error: Exception) { stop("操作停止：${error.message ?: "存储错误"}。原记录保留，请恢复核对") }
        finally {
            queued.decrementAndGet()
            runCatching { publish() }.onFailure { stop("无法读取本机数据，请停止作业并保留数据") }
        }
    }
    private fun publish() {
        if (assignment.isEmpty()) return
        if (assignment != requested) { mutable.value = WorkUi(blocked = fault, queued = queued.get()); return }
        val authError = runCatching { store.access.authorize(store.prepared(assignment)) }.exceptionOrNull()?.message.orEmpty()
        val failure = store.db.captures().failure(assignment)
        mutable.update { it.copy(order = store.db.work().order(assignment), lines = store.db.work().lines(assignment),
            candidate = failure ?: store.db.captures().pending(assignment), recent = store.db.scans().recent(assignment),
            dispositions = store.db.dispositions().all(assignment).associate { row -> row.captureId to row.action },
            pending = store.db.captures().pendingCount(assignment), queued = queued.get(), blocked = fault.ifEmpty { authError },
            ready = active(assignment) && authError.isEmpty() && store.db.work().order(assignment)?.state == "SCANNING") }
    }
}
