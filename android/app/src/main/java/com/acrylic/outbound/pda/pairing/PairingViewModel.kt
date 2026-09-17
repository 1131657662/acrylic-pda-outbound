package com.acrylic.outbound.pda.pairing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

data class PairingUi(val busy: Boolean = false, val error: String = "", val saved: JSONObject? = null, val bound: Boolean = false)

class PairingViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PdaApplication
    private val mutable = MutableStateFlow(PairingUi())
    val state = mutable.asStateFlow()
    init { run { app.pairing.saved() } }
    fun start() = run { app.pairing.start(BuildConfig.PDA_SERVER_URL) }
    fun recover() = run { app.pairing.resume() }
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
                val saved = withContext(Dispatchers.IO) { action() }
                mutable.value = PairingUi(saved = saved, bound = app.session.settings.device.isNotBlank())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val saved = withContext(Dispatchers.IO) { runCatching { app.pairing.saved() }.getOrNull() }
                mutable.value = PairingUi(error = error.message ?: "连接中断，原申请结果待核对，请恢复原请求", saved = saved)
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
