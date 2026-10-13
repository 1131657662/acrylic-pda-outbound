package com.acrylic.outbound.pda.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.*
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.TrustedTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class IndependentSkuUi(val busy: Boolean = false, val review: IndependentSkuReview? = null,
    val page: Int = 0, val issuesOnly: Boolean = false, val history: List<IndependentSkuHistory> = emptyList(),
    val message: String = "选择原sku文件进行预检；启用资料不代表可扫码或出库。")

class IndependentSkuViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as PdaApplication
    private val repository = IndependentSkuRepository(app.database) { TrustedTime.sample(app) }
    private val mutable = MutableStateFlow(IndependentSkuUi())
    internal val state = mutable.asStateFlow()
    init { load() }
    fun load() = run("正在核验已保存的原资料") {
        saved.get<String>("importId")?.let(repository::review) ?: repository.latest()
    }
    fun choose(uri: Uri) = run("正在保存原字节并预检；旧资料继续保留") {
        val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "sku.txt"
        val bytes = checkNotNull(app.contentResolver.openInputStream(uri)) { "无法读取所选文件" }
            .use(IndependentSkuBytes::read)
        repository.stage(bytes, name)
    }
    fun showActive() = run("正在核验使用中的原资料") { repository.active() }
    fun openHistory(index: Int) {
        val id = state.value.history.getOrNull(index)?.imported?.id ?: return
        run("正在核验所选原件") { repository.review(id) }
    }
    fun history(older: Boolean) {
        if (state.value.busy) return
        val before = if (older) state.value.history.lastOrNull()?.sequence ?: Long.MAX_VALUE else Long.MAX_VALUE
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { repository.history(before) }
                mutable.update { it.copy(history = page, busy = false, message = "已读取本机原件记录；请选择一项再打开。") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(busy = false, message = "原件列表读取未完成；${error.message}") }
            }
        }
    }
    fun issuesOnly(checked: Boolean) { if (!state.value.busy) mutable.update { it.copy(page = 0, issuesOnly = checked) } }
    fun activate(confirmed: Boolean) {
        val review = state.value.review ?: return
        run("正在原子启用；写入完成前不报成功") {
            repository.activate(review.imported.id, confirmed, review.activeImportId)
        }
    }
    fun cancel() {
        val id = state.value.review?.imported?.id ?: return
        run("正在取消本次预检；原件和旧资料保留") { repository.cancel(id) }
    }
    fun pickerCancelled() { if (!state.value.busy) mutable.update { it.copy(message = "未选择文件；已保存的预检原件与使用中资料保留。") } }
    fun page(delta: Int) {
        if (state.value.busy) return
        val pages = (IndependentSkuDisplay.filtered(state.value.review, state.value.issuesOnly).size + 24) / 25
        mutable.update { it.copy(page = (it.page + delta).coerceIn(0, maxOf(0, pages - 1))) }
    }
    private fun run(message: String, action: () -> IndependentSkuReview?) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, message = message) }
        viewModelScope.launch {
            try {
                val review = withContext(Dispatchers.IO) { action() }
                saved["importId"] = review?.imported?.id
                mutable.update { it.copy(busy = false, review = review, page = 0, issuesOnly = false, message = outcome(review)) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(busy = false, message = "${error.message ?: "本次操作未完成"}。原记录保留，请重新读取核对。") }
            }
        }
    }
    private fun outcome(review: IndependentSkuReview?): String = when {
        review == null -> "尚无已保存资料；请选择原sku文件。"
        review.decision?.outcome == "CANCELLED" -> "本次预检已取消；原字节保留，使用中的资料不变。"
        review.decision?.outcome == "ACTIVATED" -> if (review.activeImportId == review.imported.id)
            "资料已启用，仅供后续新独立任务引用；旧任务仍使用原快照。" else "该资料曾启用；已被后续资料替换，旧快照保留。"
        review.canActivate -> "原件已保存且预检完成；请核对下方明细和来源限制后启用。"
        else -> "原件已保存，预检存在错误，不能启用；请按原行号核对文件。"
    }
}
