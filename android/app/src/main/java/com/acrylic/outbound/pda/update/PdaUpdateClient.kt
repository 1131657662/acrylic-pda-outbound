package com.acrylic.outbound.pda.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.ConnectionSettings
import java.io.File

sealed interface UpdateCheckResult {
    data class Available(val payload: UpdatePayload) : UpdateCheckResult
    data class Current(val currentVersionCode: Long, val currentVersionName: String) : UpdateCheckResult
}

class PdaUpdateClient(private val context: Context, private val settings: ConnectionSettings,
    private val database: PdaDatabase, work: () -> com.acrylic.outbound.pda.work.WorkUi) {
    private val store = UpdateStore(context)
    private val safety = UpdateSafety(database, work)
    private val verifier = UpdateManifestVerifier(BuildConfig.PDA_UPDATE_PUBLIC_KEY)
    @Volatile private var downloading: UpdateHttp? = null

    fun savedState(): UpdateDownloadState? = store.read()

    @Synchronized fun check(): UpdateCheckResult {
        val current = currentPackage()
        val server = settings.server.ifBlank { BuildConfig.PDA_SERVER_URL }
        val raw = UpdateHttp(server, BuildConfig.PDA_UPDATE_CHANNEL).manifest()
        val signed = SignedUpdateManifest.parse(raw)
        val payload = verifier.verify(signed)
        UpdateCompatibility(context.packageName, BuildConfig.PDA_UPDATE_CHANNEL, current.longVersionCode, Build.VERSION.SDK_INT).check(payload)
        val previous = store.read()
        if (previous?.releaseId != payload.releaseId || previous.manifestJson != raw) {
            store.save(UpdateDownloadState(payload.releaseId, raw, 0, "DISCOVERED", checkedAt = System.currentTimeMillis()))
        } else store.save(previous.copy(checkedAt = System.currentTimeMillis()))
        return if (payload.versionCode > current.longVersionCode) UpdateCheckResult.Available(payload)
        else UpdateCheckResult.Current(current.longVersionCode, current.versionName ?: "")
    }

    @Synchronized fun download(payload: UpdatePayload, onProgress: (Long, Long) -> Unit): File {
        val server = settings.server.ifBlank { BuildConfig.PDA_SERVER_URL }
        val state = store.read()
        check(state?.releaseId == payload.releaseId) { "请先重新检查更新" }
        check(verifier.verify(SignedUpdateManifest.parse(state.manifestJson)) == payload) { "更新清单已变化，请重新检查" }
        check(payload.versionCode > currentPackage().longVersionCode) { "此版本已经安装，无需重复下载" }
        check(store.availableBytes() >= payload.sizeBytes + 20L * 1024 * 1024) { "设备空间不足，请清理空间后重试" }
        val part = store.temp(payload.releaseId)
        val offset = if (part.isFile && state.bytesDownloaded == part.length()) state.bytesDownloaded else 0
        if (offset == 0L && part.exists()) part.delete()
        store.save(state.copy(bytesDownloaded = offset, state = "DOWNLOADING", error = ""))
        var complete = false
        var verified = false
        return try {
            val http = UpdateHttp(server, BuildConfig.PDA_UPDATE_CHANNEL); downloading = http
            val result = if (offset == payload.sizeBytes) UpdateHttp.DownloadResult(offset, state.etag, true)
            else http.download(UpdateHttp.DownloadRequest(payload.apkPath, part, payload.sizeBytes, offset, state.etag),
                { done, total ->
                    store.save(checkNotNull(store.read()).copy(bytesDownloaded = done, state = "DOWNLOADING"))
                    onProgress(done, if (total > 0) total else payload.sizeBytes)
                },
                { responseEtag -> store.save(checkNotNull(store.read()).copy(etag = responseEtag)) })
            check(part.length() == payload.sizeBytes) { "安装包下载不完整，请重试" }
            complete = true
            ApkVerifier(context).verify(part, payload)
            verified = true
            verifyPublished(payload)
            val finalFile = store.apk(payload.releaseId); check(!finalFile.exists() || finalFile.delete()) { "无法替换旧的更新文件" }
            check(part.renameTo(finalFile)) { "更新文件保存失败，请重试" }
            store.save(checkNotNull(store.read()).copy(bytesDownloaded = payload.sizeBytes, state = "READY_TO_INSTALL", error = "", etag = result.etag ?: state.etag))
            finalFile
        } catch (error: Exception) {
            val current = checkNotNull(store.read())
            if (!verified && (complete || part.length() >= payload.sizeBytes)) {
                part.delete()
                store.save(current.copy(bytesDownloaded = 0, state = "FAILED", error = error.message ?: "安装包校验失败"))
            } else {
                store.save(current.copy(state = "FAILED", error = error.message ?: "下载未完成"))
            }
            throw error
        } finally { downloading = null }
    }

    fun cancelDownload() { downloading?.cancelDownload() }

    @Synchronized fun readyFile(payload: UpdatePayload): File {
        val state = checkNotNull(store.read()) { "尚未下载更新" }
        check(state.releaseId == payload.releaseId && state.state == "READY_TO_INSTALL") { "更新包尚未完成校验" }
        val file = store.apk(payload.releaseId)
        verifyPublished(payload)
        check(payload.versionCode > currentPackage().longVersionCode) { "此版本已经安装，无需重复安装" }
        check(store.availableBytes() >= payload.sizeBytes + 20L * 1024 * 1024) { "安装空间不足，请联系管理员核对" }
        ApkVerifier(context).verify(file, payload)
        val blocked = safety.installBlockReason()
        check(blocked == null) { blocked!! }
        return file
    }

    private fun verifyPublished(payload: UpdatePayload) {
        val server = settings.server.ifBlank { BuildConfig.PDA_SERVER_URL }
        val current = verifier.verify(SignedUpdateManifest.parse(UpdateHttp(server, BuildConfig.PDA_UPDATE_CHANNEL).manifest()))
        check(current == payload) { "更新发布已变更或撤回，请重新检查；已下载资料保留" }
        UpdateCompatibility(context.packageName, BuildConfig.PDA_UPDATE_CHANNEL, currentPackage().longVersionCode, Build.VERSION.SDK_INT).check(current)
    }

    fun verifyInstalledData() {
        database.openHelper.readableDatabase.query("PRAGMA quick_check(1)").use {
            check(it.moveToFirst() && it.getString(0) == "ok") { "升级后的本机资料需核对，请保留数据并联系管理员" }
        }
        if (settings.device.isNotBlank()) settings.verifyKey(com.acrylic.outbound.pda.security.DeviceKeys().publicKey(false))
    }

    fun markInstallPending(payload: UpdatePayload) {
        val state = checkNotNull(store.read())
        check(state.releaseId == payload.releaseId) { "更新发布已变化，请重新检查" }
        store.save(state.copy(state = "INSTALL_PENDING", error = ""))
    }

    fun markInstallReady(payload: UpdatePayload) {
        val state = store.read() ?: return
        if (state.releaseId == payload.releaseId && state.state == "INSTALL_PENDING") {
            store.save(state.copy(state = "READY_TO_INSTALL", error = ""))
        }
    }

    fun installBlockReason(): String? = safety.installBlockReason()

    private fun currentPackage(): android.content.pm.PackageInfo = context.packageManager.getPackageInfo(
        context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
}
