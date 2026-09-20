package com.acrylic.outbound.pda.update

import android.util.Base64
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class UpdateManifestVerifier(private val publicKeyBase64: String) {
    fun verify(manifest: SignedUpdateManifest): UpdatePayload {
        check(publicKeyBase64.isNotBlank()) { "更新签名公钥未配置，请使用正确的测试安装包" }
        val keyBytes = runCatching { Base64.decode(publicKeyBase64, Base64.DEFAULT) }
            .getOrElse { error("更新签名公钥格式无效") }
        val key = runCatching { KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes)) }
            .getOrElse { error("更新签名公钥无效") }
        val valid = runCatching {
            Signature.getInstance("SHA256withECDSA").apply {
                initVerify(key); update(manifest.rawPayload)
            }.verify(manifest.signature)
        }.getOrDefault(false)
        check(valid) { "更新清单签名校验失败，请联系管理员" }
        return manifest.payload
    }
}

class UpdateCompatibility(private val packageName: String, private val channel: String,
    private val currentVersionCode: Long, private val sdk: Int) {
    fun check(payload: UpdatePayload) {
        check(payload.packageName == packageName) { "更新包不是本机应用" }
        check(payload.channel == channel) { "更新包渠道不匹配，不能跨渠道覆盖" }
        check(payload.apkPath.startsWith("updates/$channel/")) { "安装包地址不属于当前渠道" }
        check(payload.serverCompatibility in setOf("0.17.0", "0.18.0", "0.19.0", "0.20.0")) { "服务端兼容范围尚未验证，请联系管理员核对" }
        check(payload.minAndroidApi <= sdk) { "此版本需要更高的 Android 系统" }
        check(currentVersionCode in payload.minSupportedVersionCode..(payload.maxSupportedVersionCode ?: Long.MAX_VALUE)) {
            "当前版本不在可直接升级范围，请联系管理员"
        }
    }
}
