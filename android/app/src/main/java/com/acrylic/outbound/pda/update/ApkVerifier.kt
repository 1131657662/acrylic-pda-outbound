package com.acrylic.outbound.pda.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

class ApkVerifier(private val context: Context) {
    fun verify(file: File, payload: UpdatePayload) {
        check(file.isFile && file.length() == payload.sizeBytes) { "安装包大小不匹配" }
        check(sha256(file).equals(payload.sha256, ignoreCase = true)) { "安装包摘要校验失败，已清理无效文件" }
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("无法读取安装包身份")
        check(archive.packageName == payload.packageName) { "安装包包名不匹配" }
        check(archive.longVersionCode == payload.versionCode) { "安装包版本号不匹配" }
        check(archive.versionName == payload.versionName) { "安装包版本名称不匹配" }
        check(archive.applicationInfo?.minSdkVersion == payload.minAndroidApi) { "安装包系统要求不匹配" }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val installedCert = certificateDigest(installed)
        val archiveCert = certificateDigest(archive)
        check(installedCert == archiveCert && archiveCert.equals(payload.certificateSha256, ignoreCase = true)) {
            "安装包签名证书不匹配，不能覆盖当前应用"
        }
    }

    private fun certificateDigest(info: android.content.pm.PackageInfo): String {
        // PackageManager may expose only the legacy signatures array for an APK
        // archive on Android 9 even though installed packages expose SigningInfo.
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners ?: info.signatures
        } else info.signatures
        check(signatures != null && signatures.size == 1) { "安装包签名证书数量不受支持" }
        return sha256(signatures[0].toByteArray())
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
