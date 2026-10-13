package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.update.UpdateGate
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal data class IndependentWorkUi(val task: IndependentTaskView? = null, val candidate: IndependentCaptureView? = null,
    val rows: List<IndependentScanView> = emptyList(), val selected: IndependentScanView? = null, val after: Long = 0,
    val queued: Int = 0, val ready: Boolean = false, val blocked: String = "", val message: String = "独立采集始终待主管对账。")

/** Application-owned serial queue; a pause stops automatic processing after the original input is durable. */
internal class IndependentScanEngine(private val db: PdaDatabase, private val environment: IndependentScanEnvironment,
    private val unlock: (CharArray) -> IndependentCaptureGrant) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val queue = Channel<() -> Unit>(64)
    private val queued = AtomicInteger()
    private val mutable = MutableStateFlow(IndependentWorkUi())
    val state = mutable.asStateFlow()
    @Volatile private var requested = ""
    @Volatile private var current = ""
    @Volatile private var foreground = false
    @Volatile private var fault = ""
    @Volatile private var sealing = false
    private var lease: AutoCloseable? = null
    private var processor: IndependentScanProcessor? = null
    val scanning get() = foreground
    init {
        scope.launch { for (action in queue) execute(action) }
        scope.launch { while (isActive) { delay(1000); if (foreground && queued.get() == 0) runCatching { publish() }.onFailure { stop(it.message.orEmpty()) } } }
    }
    @Synchronized fun resume(id: String) {
        if (sealing) { mutable.update { it.copy(message = "正在固定结束边界，请等待原结果") }; return }
        if (lease == null) lease = try { UpdateGate.enterBusiness() } catch (error: Exception) { stop(error.message.orEmpty()); return }
        requested = id; foreground = true
        if (id != current) mutable.value = IndependentWorkUi() else mutable.update { it.copy(ready = false) }
        enqueue {
            IndependentTaskRepository(db, environment.sample, environment.actor).read(id)
            if (id != current) { processor = null; fault = "" }
            current = id; processor?.store?.check(id); drain()
        }
    }
    @Synchronized fun pause() { foreground = false; mutable.update { it.copy(ready = false) }; lease?.close(); lease = null }
    fun authorize(id: String, secret: CharArray) {
        if (!enqueue {
            try {
                check(foreground && requested == id) { "请先恢复所选原任务页面" }
                val store = IndependentScanStore(db, unlock(secret), environment); store.open(id)
                IndependentScanSaving(store).start(id)
                processor = IndependentScanProcessor(store, ::feedback)
                current = id; fault = ""; drain()
            } finally { secret.fill('\u0000') }
        }) secret.fill('\u0000')
    }
    @Synchronized fun receive(raw: String, source: String): Boolean {
        if (!active(current) || !state.value.ready) { mutable.update { it.copy(message = "本条未接收；请核验口令、恢复作业并处理未决输入。") }; return false }
        val worker = checkNotNull(processor); val input = IndependentInput(current, raw, source, environment.sample())
        return enqueue { worker.reception.receive(input); if (worker === processor) drain() }
    }
    fun confirm(id: String) = enqueue { check(active(current)); checkNotNull(processor).saving.confirm(id); drain() }
    fun acknowledge(id: String) = enqueue { checkNotNull(processor).corrections.acknowledge(id); drain() }
    fun discard(id: String, reason: String) = enqueue { checkNotNull(processor).corrections.discard(id, reason); drain() }
    fun locate(barcode: String) = enqueue {
        environment.actor().requireOwner(checkNotNull(db.independentTasks().task(current)))
        mutable.update { it.copy(selected = db.independentScans().locate(current, barcode.trim().uppercase(java.util.Locale.ROOT)),
            message = "已按原条码定位；核实实物卸下或未装车后方可撤销。") }
    }
    fun page(older: Boolean) = enqueue {
        val after = if (older) state.value.rows.lastOrNull()?.scan?.sequence ?: 0 else 0
        mutable.update { it.copy(after = after, rows = db.independentScans().page(current, after)) }
    }
    fun voidSelected(id: String, reason: String) = enqueue {
        check(!foreground) { "请先暂停扫码，再核对原包" }
        checkNotNull(processor).corrections.voidSelected(current, id, reason)
        mutable.update { it.copy(selected = null, message = "原包已VOID，原扫码和审计保留；独立来源仍待对账。") }
    }
    @Synchronized fun finish(reason: String, confirmed: Boolean) {
        if (foreground || sealing || queued.get() != 0 || current != requested || current.isBlank()) {
            mutable.update { it.copy(message = "请先暂停扫码并等待队列排空，再核对结束") }; return
        }
        if (!confirmed) { mutable.update { it.copy(message = "请勾选结束后不可新增或撤销，仍待主管对账") }; return }
        sealing = true
        if (!enqueue {
            try {
                val worker = checkNotNull(processor) { "请先核验原任务采集口令，再暂停并结束" }
                IndependentTaskClosing(worker.store, environment.closureSigner).close(current, reason, true)
                processor = null; fault = ""
                mutable.update { it.copy(message = "独立采集已结束并封存；原件保留，待主管实物核对与人工关联。") }
            } finally { sealing = false }
        }) sealing = false
    }
    fun stop(message: String) { fault = message.ifBlank { "独立输入或存储异常，请停扫保全" }; mutable.update { it.copy(ready = false, blocked = fault) } }
    fun close() { pause(); scope.cancel(); dispatcher.close() }
    private fun active(id: String) = foreground && id.isNotEmpty() && id == current && id == requested && fault.isEmpty()
    private fun feedback(result: String) {
        mutable.update { it.copy(message = if (result == "SAVED") "已保存到本机；独立采集待主管对账，不得放行。"
            else "本包未保存，请核对并处理失败输入。") }
        if (foreground) environment.feedback(result)
    }
    private fun drain() { processor?.drain(current) { active(current) } }
    private fun enqueue(action: () -> Unit): Boolean {
        val taskLease = try { UpdateGate.enterBusiness() } catch (error: Exception) { stop(error.message.orEmpty()); return false }
        queued.incrementAndGet(); mutable.update { it.copy(queued = queued.get()) }
        if (queue.trySend { try { action() } finally { taskLease.close() } }.isSuccess) return true
        taskLease.close(); queued.decrementAndGet(); stop("入口已满，本条未接收，请停扫核对")
        return false
    }
    private fun execute(action: () -> Unit) {
        try { action() } catch (error: Exception) { stop("${error.message ?: "本条尚未确认落盘"}；原记录保留，请核验口令后恢复") }
        finally { queued.decrementAndGet(); runCatching { publish() }.onFailure { stop("无法读取原独立记录，请停止作业并保留资料") } }
    }
    private fun publish() {
        if (current.isEmpty() || current != requested) { mutable.update { it.copy(ready = false, queued = queued.get()) }; return }
        val original = IndependentTaskRepository(db, environment.sample, environment.actor).read(current)
        if (original.state.state == "SEALED") { pause(); processor = null; fault = "" }
        val auth = if (original.state.state == "SEALED") "已结束封存；仅可核对、导出原件，仍待主管对账。"
            else runCatching { checkNotNull(processor) { "请核验独立口令以开始或恢复采集" }.store.check(current) }.exceptionOrNull()?.message.orEmpty()
        val candidate = db.independentScans().failure(current) ?: db.independentScans().pending(current)
        mutable.update { it.copy(task = original, candidate = candidate, queued = queued.get(), rows = db.independentScans().page(current, it.after),
            blocked = fault.ifEmpty { auth }, ready = active(current) && auth.isEmpty() && candidate == null) }
    }
}
