package com.acrylic.outbound.pda.update

import org.json.JSONObject

data class UpdatePayload(
    val releaseId: String,
    val channel: String,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val minAndroidApi: Int,
    val minSupportedVersionCode: Long,
    val maxSupportedVersionCode: Long?,
    val apkPath: String,
    val sizeBytes: Long,
    val sha256: String,
    val certificateSha256: String,
    val publishedAt: String,
    val releaseNotes: String,
    val serverCompatibility: String
) {
    companion object {
        fun parse(json: String): UpdatePayload {
            val value = JSONObject(json)
            val max = if (value.isNull("maxSupportedVersionCode")) null else value.getLong("maxSupportedVersionCode")
            return UpdatePayload(
                value.getString("releaseId"), value.getString("channel"), value.getString("packageName"),
                value.getLong("versionCode"), value.getString("versionName"), value.getInt("minAndroidApi"),
                value.getLong("minSupportedVersionCode"), max, value.getString("apkPath"),
                value.getLong("sizeBytes"), value.getString("sha256"), value.getString("certificateSha256"),
                value.getString("publishedAt"), value.getString("releaseNotes"), value.getString("serverCompatibility")
            ).also { it.validate() }
        }
    }

    fun validate() {
        require(releaseId.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "发布标识无效" }
        require(channel in setOf("debug", "staging", "production")) { "更新渠道无效" }
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) { "包名无效" }
        require(versionCode > 0 && minAndroidApi in 1..100 && minSupportedVersionCode > 0) { "版本范围无效" }
        require(maxSupportedVersionCode == null || maxSupportedVersionCode >= minSupportedVersionCode) { "版本范围无效" }
        require(apkPath.matches(Regex("updates/(debug|staging|production)/[A-Za-z0-9._-]{1,120}\\.apk"))) { "安装包路径无效" }
        require(sizeBytes > 0 && sizeBytes <= 64L * 1024 * 1024) { "安装包大小无效" }
        require(sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "安装包摘要无效" }
        require(certificateSha256.matches(Regex("[a-fA-F0-9]{64}"))) { "签名证书摘要无效" }
        require(publishedAt.isNotBlank() && serverCompatibility.isNotBlank()) { "发布信息不完整" }
    }
}

data class SignedUpdateManifest(val payload: UpdatePayload, val rawPayload: ByteArray, val signature: ByteArray) {
    companion object {
        fun parse(json: String): SignedUpdateManifest {
            val outer = JSONObject(json)
            val raw = android.util.Base64.decode(outer.getString("payload"), android.util.Base64.DEFAULT)
            val signature = android.util.Base64.decode(outer.getString("signature"), android.util.Base64.DEFAULT)
            require(raw.size in 2..128 * 1024 && signature.isNotEmpty()) { "更新清单签名格式无效" }
            return SignedUpdateManifest(UpdatePayload.parse(String(raw, Charsets.UTF_8)), raw, signature)
        }
    }
}

data class UpdateDownloadState(
    val releaseId: String,
    val manifestJson: String,
    val bytesDownloaded: Long,
    val state: String,
    val error: String = "",
    val etag: String? = null,
    val checkedAt: Long = 0
) {
    fun toJson() = JSONObject().put("releaseId", releaseId).put("manifestJson", manifestJson)
        .put("bytesDownloaded", bytesDownloaded).put("state", state).put("error", error)
        .put("etag", etag ?: JSONObject.NULL).put("checkedAt", checkedAt)

    companion object {
        fun fromJson(value: JSONObject) = UpdateDownloadState(value.getString("releaseId"), value.getString("manifestJson"),
            value.getLong("bytesDownloaded"), value.getString("state"), value.optString("error"),
            value.optString("etag").takeUnless { it.isBlank() || it == "null" }, value.optLong("checkedAt"))
    }
}
