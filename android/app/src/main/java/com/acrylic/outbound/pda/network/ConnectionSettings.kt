package com.acrylic.outbound.pda.network

import android.content.Context
import com.acrylic.outbound.pda.BuildConfig
import java.net.URI
import java.util.UUID
import android.util.AtomicFile
import java.io.File
import org.json.JSONObject

class ConnectionSettings(context: Context) {
    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
    private val file = AtomicFile(File(context.noBackupFilesDir, "device-binding.json"))
    val server: String get() = read()?.getString("server") ?: prefs.getString("server", "")!!
    val device: String get() = read()?.getString("device") ?: prefs.getString("device", "")!!
    private val publicKey: String get() = read()?.getString("key") ?: prefs.getString("key", "")!!

    fun bind(server: String, device: String, publicKey: String) = synchronized(bindingLock) {
        val url = validateServer(server)
        val id = UUID.fromString(device.trim()).toString()
        check(this.device.isBlank() || (this.device == id && this.server == url && this.publicKey == publicKey)) { "本机已经绑定，换机或改服务地址需先办理交接" }
        val bytes = JSONObject().put("server", url).put("device", id).put("key", publicKey).toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        check(file.readFully().contentEquals(bytes)) { "设备绑定未完整保存，请恢复原登记结果" }
    }

    private fun read(): JSONObject? = synchronized(bindingLock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized null
        JSONObject(String(file.readFully(), Charsets.UTF_8))
    }
    fun verifyKey(publicKey: String) { check(device.isBlank() || this.publicKey == publicKey) { "设备私钥身份已变化，请联系管理员" } }
    companion object {
        private val bindingLock = Any()
        fun validateServer(server: String): String {
            val url = URI(server.trim().trimEnd('/') + "/")
            check(url.userInfo == null && url.query == null && url.fragment == null && url.host != null) { "服务器地址无效" }
            check(url.path == "/api/v1/") { "服务器地址须以 /api/v1/ 结尾" }
            check(url.scheme == "https" || (BuildConfig.DEBUG && url.scheme == "http" && url.host in setOf("127.0.0.1", "localhost", "10.0.2.2"))) { "正式连接必须使用HTTPS" }
            return url.toString()
        }
    }
}
