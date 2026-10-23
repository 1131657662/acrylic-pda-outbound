package com.acrylic.outbound.pda.ui

import android.content.Context
import android.net.Uri
import com.acrylic.outbound.pda.rescue.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Tracks only the current plain-text save attempt, separately from encrypted-package copies. */
internal class RescueTextExport(context: Context, private val state: MutableStateFlow<RescueExportUi>) {
    private val files = RescueOutTextFiles(context)

    fun read(snapshot: RescueSnapshot) {
        val eligible = runCatching { RescueOutText.encode(snapshot) }
        val status = if (eligible.isSuccess) "out.txt 可直接导出：无表头、TAB 分隔、GBK 编码。"
            else "当前记录不能生成旧式 out.txt：${eligible.exceptionOrNull()?.message ?: "缺少可信原操作员姓名"}"
        state.update { it.copy(canExportText = eligible.isSuccess, textExportState = status) }
    }

    fun begin(): Boolean {
        if (state.value.busy || !state.value.canExportText) return false
        state.update { it.copy(textExportState = "请选择 out.txt 保存位置；本次尚未写入。") }
        return true
    }

    fun cancel() = state.update { it.copy(textExportState = "out.txt 保存已取消；本次未写入，原记录保留。") }

    fun copy(snapshot: RescueSnapshot?, uri: Uri, grant: RescueReadGrant?) {
        state.update { it.copy(textExportState = "正在写入 out.txt 并回读校验；本次尚未完成。") }
        try {
            check(state.value.canExportText) { "当前快照没有可信原操作员姓名，不能生成 out.txt" }
            val digest = files.copy(checkNotNull(snapshot), uri, checkNotNull(grant))
            state.update { it.copy(textExportState = "out.txt 写入与回读一致。SHA256 $digest") }
        } catch (error: Exception) {
            state.update { it.copy(textExportState = "本次 out.txt 保存未完成；请勿使用半文件，原记录保留。") }
            throw error
        }
    }
}
