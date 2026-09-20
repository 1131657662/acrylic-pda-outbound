package com.acrylic.outbound.pda.ui

import android.app.Application
import androidx.lifecycle.*
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.temporary.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class TemporaryDraftUi(val busy: Boolean = true, val draft: TemporaryDraft? = null,
    val products: List<TemporaryProduct> = emptyList(), val lines: List<TemporaryLine> = emptyList(), val message: String = "正在恢复原草稿")

class TemporaryDraftViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val id: String = saved.get<String>("clientOrderId") ?: UUID.randomUUID().toString().also { saved["clientOrderId"] = it }
    private val store = (application as PdaApplication).temporaryStore()
    private val repository = TemporaryDraftRepository(store)
    private val mutable = MutableStateFlow(TemporaryDraftUi())
    val state = mutable.asStateFlow()
    init { run { repository.create(id); load("草稿身份已保存；填写后请保存计划") } }
    fun retry() { if (!mutable.value.busy) run { repository.create(id); load("已恢复原草稿") } }
    fun save(input: TemporaryPlanInput, start: Boolean) {
        if (mutable.value.busy) return
        run {
            val draft = repository.save(id, input)
            if (start) repository.start(id, draft.revision)
            load(if (start) "已开始本机作业，未确认不得放行" else "草稿已保存")
        }
    }
    private fun load(message: String) {
        val draft = store.load(id)
        mutable.value = TemporaryDraftUi(true, draft, store.source(id).products.values.sortedBy { it.code },
            store.db.temporaryDrafts().lines(id), message)
    }
    private fun run(action: () -> Unit) {
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (error: Exception) { mutable.update { it.copy(message = "${error.message ?: "保存失败"}；原草稿保留") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
