package com.acrylic.outbound.pda.pairing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

data class PairingUi(val busy: Boolean = false, val error: String = "", val saved: JSONObject? = null,
    val bound: Boolean = false, val storageFailed: Boolean = false)

class PairingViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PdaApplication
    private val mutable = MutableStateFlow(PairingUi())
    val state = mutable.asStateFlow()
    init { readSaved() }
    private fun readSaved() = run { app.pairing.saved() }
    fun start() = run { app.pairing.start(BuildConfig.PDA_SERVER_URL) }
    fun recover() {
        if (mutable.value.storageFailed) readSaved() else run { app.pairing.resume() }
    }
    fun confirm() = run { app.pairing.confirm() }
    fun cancel() = run { app.pairing.cancel() }
    suspend fun pollWhileVisible() {
        while (currentCoroutineContext().isActive) {
            delay(4000)
            val current = mutable.value
            if (!current.busy && current.error.isEmpty() && current.saved?.optJSONObject("result")?.optString("status") in setOf("WAITING", "PENDING")) recover()
        }
    }
    private fun run(action: () -> JSONObject?) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = "") }
        viewModelScope.launch {
            try {
                mutable.value = withContext(Dispatchers.IO) {
                    PairingUi(saved = action(), bound = app.session.settings.device.isNotBlank())
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                restoreAfterFailure(error)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }

    private suspend fun restoreAfterFailure(error: Exception) {
        try {
            mutable.value = withContext(Dispatchers.IO) {
                PairingUi(error = error.message ?: "连接中断，请恢复原申请", saved = app.pairing.saved(),
                    bound = app.session.settings.device.isNotBlank())
            }
        } catch (readError: Exception) {
            if (readError is CancellationException) throw readError
            mutable.update { it.copy(error = "无法读取本机登记申请。请保留资料并重试读取；仍失败时联系管理员。",
                storageFailed = true) }
        }
    }
}
