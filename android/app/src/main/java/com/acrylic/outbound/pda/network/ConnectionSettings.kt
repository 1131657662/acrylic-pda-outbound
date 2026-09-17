package com.acrylic.outbound.pda.network

import android.content.Context
import com.acrylic.outbound.pda.BuildConfig
import java.net.URI
import java.util.UUID

class ConnectionSettings(context: Context) {
    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
    val server: String get() = prefs.getString("server", "")!!
    val device: String get() = prefs.getString("device", "")!!

    fun bind(server: String, device: String, publicKey: String) {
        val url = URI(server.trim().trimEnd('/') + "/")
        check(url.userInfo == null && url.query == null && url.fragment == null && url.host != null) { "服务器地址无效" }
        check(url.path == "/api/v1/") { "服务器地址须以 /api/v1/ 结尾" }
        check(url.scheme == "https" || (BuildConfig.DEBUG && url.scheme == "http" && url.host in setOf("127.0.0.1", "localhost", "10.0.2.2"))) { "正式连接必须使用HTTPS" }
        val id = UUID.fromString(device.trim()).toString()
        check(this.device.isBlank() || (this.device == id && this.server == url.toString() && prefs.getString("key", "") == publicKey)) { "本机已经绑定，换机或改服务地址需先办理交接" }
        check(prefs.edit().putString("server", url.toString()).putString("device", id).putString("key", publicKey).commit())
    }

    fun verifyKey(publicKey: String) { check(device.isBlank() || prefs.getString("key", "") == publicKey) { "设备私钥身份已变化，请联系管理员" } }
}
