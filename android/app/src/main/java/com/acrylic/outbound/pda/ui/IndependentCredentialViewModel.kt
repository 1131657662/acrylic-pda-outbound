package com.acrylic.outbound.pda.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.independent.IndependentCredentials
import com.acrylic.outbound.pda.independent.IndependentCredentialCard
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.sync.RecoveryProtection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Base64
import org.json.JSONObject

internal data class IndependentCredentialUi(val busy: Boolean = false, val original: String = "",
    val message: String = "预置需联网核验；口令仅在本机用于独立身份。")

class IndependentCredentialViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PdaApplication
    private val credentials = IndependentCredentials.shared(app, app.session, RecoveryProtection(app.database))
    private val mutable = MutableStateFlow(IndependentCredentialUi())
    internal val state = mutable.asStateFlow()
    init { reload() }
    fun reload(message: String = "已读取原件；新增采集仍须核验口令和可信期限。") = run("正在读取已保存的身份原件") { message }
    fun provision(password: CharArray) = run("正在在线核验并保存签名原件；未确认请求沿用原键", password) { credentials.provision(password) }
    fun verify(password: CharArray) = run("正在本机核验身份和可信期限", password) { credentials.verifyLocally(password) }
    internal fun cardSelection(): JSONObject? = runCatching {
        check(!state.value.busy)
        val actor = checkNotNull(app.session.saved())
        val expected = JSONObject()
        listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "localSessionId", "userAuthVersion", "deviceVersion")
            .forEach { expected.put(it, actor.getString(it)) }
        val public = DeviceKeys().publicKey(false).also(app.session.settings::verifyKey)
        expected.put("deviceKeySha256", JwtVerifier.sha(Base64.getDecoder().decode(public)))
    }.getOrNull()
    internal fun cardMessage(message: String) { if (!state.value.busy) mutable.update { it.copy(message = message) } }
    internal fun verifyCard(uri: Uri, expected: JSONObject) = run("正在读取独立凭据卡并核验原身份和可信期限") {
        try {
            IndependentCredentialCard.read(checkNotNull(app.contentResolver.openInputStream(uri))).use {
                credentials.verifyCard(it, expected)
            }
        } catch (_: Exception) {
            error("独立凭据卡未通过本机核验，请核对原卡、原身份、可信期限及尝试限制；原资料保留。")
        }
    }
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
