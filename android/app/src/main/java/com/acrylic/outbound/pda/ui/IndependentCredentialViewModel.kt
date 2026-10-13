package com.acrylic.outbound.pda.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.independent.IndependentCredentials
import com.acrylic.outbound.pda.sync.RecoveryProtection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class IndependentCredentialUi(val busy: Boolean = false, val original: String = "",
    val message: String = "预置需联网核验；口令仅在本机用于独立身份。")

class IndependentCredentialViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PdaApplication
    private val credentials = IndependentCredentials.shared(app, app.session, RecoveryProtection(app.database))
    private val mutable = MutableStateFlow(IndependentCredentialUi())
    internal val state = mutable.asStateFlow()
    init { reload() }
    fun reload() = run("正在读取已保存的身份原件") { "已读取原件；新增采集仍须核验口令和可信期限。" }
    fun provision(password: CharArray) = run("正在在线核验并保存签名原件；未确认请求沿用原键", password) { credentials.provision(password) }
    fun verify(password: CharArray) = run("正在本机核验身份和可信期限", password) { credentials.verifyLocally(password) }
    fun revoke() = run("正在撤销本机新增采集资格") { credentials.revoke(); "本机新增采集已撤销；签名原件和旧资料保留。" }
    private fun run(message: String, secret: CharArray? = null, action: () -> String) {
        if (state.value.busy) { secret?.fill('\u0000'); return }
        mutable.update { it.copy(busy = true, message = message) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { action() to credentials.status() }
                mutable.value = IndependentCredentialUi(original = result.second, message = result.first)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { it.copy(busy = false, message = "${error.message ?: "核验未完成"}。原件保留，可读取后按原请求重试。") }
            } finally { secret?.fill('\u0000') }
        }
    }
}
