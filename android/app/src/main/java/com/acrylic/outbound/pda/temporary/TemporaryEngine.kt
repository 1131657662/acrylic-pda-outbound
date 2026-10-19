package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.update.UpdateGate
import com.acrylic.outbound.pda.work.ScanProductTotal
import com.acrylic.outbound.pda.work.ScanTotal
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import com.acrylic.outbound.pda.evidence.LocalDraftStop
import com.acrylic.outbound.pda.evidence.LocalDraftStopRepository

data class TemporaryWorkUi(val draft: TemporaryDraft? = null, val lines: List<TemporaryLine> = emptyList(),
    val total: ScanTotal = ScanTotal(0, 0), val products: List<ScanProductTotal> = emptyList(),
    val candidate: TemporaryCapture? = null, val recent: List<TemporaryScan> = emptyList(),
    val queued: Int = 0, val ready: Boolean = false, val blocked: String = "", val message: String = "正在恢复临时作业")

/** One application-owned writer; a paused screen keeps accepted inputs durable. */
class TemporaryEngine(private val store: TemporaryWorkStore, private val sound: (String) -> Unit) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue = Channel<() -> Unit>(64)
    private val count = AtomicInteger()
    private val undurable = ConcurrentHashMap<String, TemporaryInput>()
    private var preserving: Deferred<LocalDraftStop>? = null
    private val mutable = MutableStateFlow(TemporaryWorkUi())
    val state = mutable.asStateFlow()
    @Volatile private var requested = ""
    @Volatile private var current = ""
    @Volatile private var foreground = false
    @Volatile private var fault = ""
    private var lease: AutoCloseable? = null
    private val processor = TemporaryProcessor(store) {
        if (foreground) sound(it)
        mutable.update { ui -> ui.copy(message = if (it == "SAVED") "已保存到本机，尚未上传；不得放行" else "本包未保存，请确认处理结果") }
    }
    init {
        scope.launch { for (action in queue) execute(action) }
        scope.launch { while (isActive) { delay(1000); if (foreground && count.get() == 0) runCatching { publish() }.onFailure { stop(it.message.orEmpty()) } } }
    }
    @Synchronized fun resume(id: String) {
        if (preserving?.isCompleted == false) return
        if (lease == null) lease = try { UpdateGate.enterBusiness() } catch (error: Exception) { stop(error.message.orEmpty()); return }
        requested = id; foreground = true
        if (id != current) mutable.value = TemporaryWorkUi() else mutable.update { it.copy(ready = false) }
        enqueue {
            val draft = store.load(id); store.requireNoOtherInput(id)
            check(store.canScan(draft)) { "请先保存计划并开始本单" }
            current = id; fault = ""; processor.drain(id) { active(id) }
        }
    }
    @Synchronized fun pause() { foreground = false; mutable.update { it.copy(ready = false) }; lease?.close(); lease = null }
    @Synchronized fun receive(raw: String, source: String): Boolean {
        if (preserving?.isCompleted == false || !active(requested) || !mutable.value.ready) { sound("ERROR"); return false }
        val input = TemporaryInput(current, raw, source, store.sample())
        undurable[input.id] = input
        val accepted = enqueue {
            processor.reception.receive(input); undurable.remove(input.id)
            processor.drain(input.order) { active(input.order) }
        }
        if (!accepted) undurable.remove(input.id)
        return accepted
    }
    /** Fence intake immediately, then preserve only after every preceding accepted input has reached Room. */
    @Synchronized fun preserve(id: String): Deferred<LocalDraftStop> {
        val result = CompletableDeferred<LocalDraftStop>()
        if (preserving?.isCompleted == false) {
            result.completeExceptionally(IllegalStateException("本机正在停止保全，请等待原操作")); return result
        }
        pause(); preserving = result
        val accepted = enqueue {
            try {
                check(undurable.isEmpty()) { "已有接收输入尚未耐久保存，不得生成停止证明，请保留原机核对" }
                val stopped = LocalDraftStopRepository(store).stop(id)
                current = id; requested = id; fault = ""
                result.complete(stopped)
            } catch (error: Exception) { result.completeExceptionally(error); throw error }
        }
        if (!accepted) result.completeExceptionally(IllegalStateException("停止操作尚未进入队列，请等待已有输入落盘后重试"))
        return result
    }
    fun confirm(id: String) = enqueue { check(active(current)); processor.saving.confirm(id); processor.drain(current) { active(current) } }
    fun acknowledge(id: String) = enqueue {
        processor.corrections.acknowledge(id)
        mutable.update { it.copy(message = "已确认本包未保存，原输入保留") }
        processor.drain(current) { active(current) }
    }
    fun discard(id: String, reason: String) = enqueue { processor.corrections.discard(id, reason); processor.drain(current) { active(current) } }
    fun voidLast(id: String, reason: String) = enqueue { processor.corrections.voidLast(current, id, reason) }
    fun adjust(order: String, capture: String, reason: String): Deferred<Unit> {
        val result = CompletableDeferred<Unit>()
        if (!enqueue {
            try { check(!foreground) { "请先暂停扫码再调整" }; store.load(order); processor.corrections.voidSelected(order, capture, reason); result.complete(Unit) }
            catch (error: Exception) { result.completeExceptionally(error) }
        }) result.completeExceptionally(IllegalStateException("调整未接收，请保留原记录并重试"))
        return result
    }
    fun recover() = resume(requested)
    @Synchronized fun cancelDraft(id: String, input: TemporaryCancelInput): Deferred<TemporaryDraft> {
        val result = CompletableDeferred<TemporaryDraft>()
        if (preserving?.isCompleted == false) {
            result.completeExceptionally(IllegalStateException("正在停止保全，请等待原操作")); return result
        }
        pause()
        if (!enqueue {
            try {
                check(undurable.isEmpty()) { "已有接收输入尚未落盘，不能取消，请保留原机核对" }
                val row = TemporaryDraftCancellation(store).cancel(id, input)
                current = id; requested = id; fault = ""
                mutable.update { it.copy(message = "已取消本机零作业草稿；原记录保留，未作服务端取消") }
                result.complete(row)
            } catch (error: Exception) { result.completeExceptionally(error) }
        }) result.completeExceptionally(IllegalStateException("取消未进入队列，请等待已有输入处理后重试"))
        return result
    }
    fun stop(message: String) { fault = message.ifBlank { "存储或输入异常，请停扫核对，原记录保留" }; mutable.update { it.copy(ready = false, blocked = fault) }; sound("ERROR") }
    fun close() { pause(); scope.cancel(); dispatcher.close() }
    private fun active(id: String) = foreground && requested == id && current == id && fault.isEmpty()
    private fun enqueue(action: () -> Unit): Boolean {
        val taskLease = try { UpdateGate.enterBusiness() } catch (error: Exception) { stop(error.message.orEmpty()); return false }
        count.incrementAndGet(); mutable.update { it.copy(queued = count.get()) }
        if (queue.trySend { try { action() } finally { taskLease.close() } }.isSuccess) return true
        taskLease.close(); count.decrementAndGet()
        mutable.update { it.copy(queued = count.get()) }
        stop("入口已满，本次未接收，请停扫核对")
        return false
    }
    private fun execute(action: () -> Unit) {
        try { action() } catch (error: Exception) { stop("${error.message ?: "存储失败"}；原记录保留") }
        finally { count.decrementAndGet(); runCatching { publish() }.onFailure { stop("无法读取临时作业，请保留数据并停止作业") } }
    }
    private fun publish() {
        if (current.isEmpty() || current != requested) { mutable.update { it.copy(ready = false, blocked = fault, queued = count.get()) }; return }
        val error = runCatching { store.check(current, store.db.localDraftStops().get(current) == null); store.requireNoOtherInput(current) }.exceptionOrNull()?.message.orEmpty()
        val scans = store.db.temporaryScans(); val draft = store.db.temporaryDrafts().get(current)
        mutable.update { it.copy(draft = draft, lines = store.db.temporaryDrafts().lines(current), total = store.total(current),
            products = store.products(current), candidate = scans.failure(current) ?: scans.pending(current), recent = scans.recent(current),
            queued = count.get(), blocked = fault.ifEmpty { error }, ready = active(current) && error.isEmpty() && draft?.let(store::canScan) == true) }
    }
}
