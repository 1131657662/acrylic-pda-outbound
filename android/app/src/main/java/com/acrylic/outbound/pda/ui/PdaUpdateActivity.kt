package com.acrylic.outbound.pda.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityPdaUpdateBinding
import com.acrylic.outbound.pda.display.DisplayTime
import com.acrylic.outbound.pda.update.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdaUpdateActivity : ComponentActivity() {
    private lateinit var binding: ActivityPdaUpdateBinding
    private val app get() = application as PdaApplication
    private var payload: UpdatePayload? = null
    private var busy = false
    private var downloading = false
    private val installer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        UpdateGate.endInstall()
        restorePending()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityPdaUpdateBinding.inflate(layoutInflater); setContentView(binding.root)
        binding.back.setOnClickListener { if (!busy) finish() }
        binding.check.setOnClickListener { checkUpdate() }
        binding.download.setOnClickListener { download() }
        binding.install.setOnClickListener { install() }
        binding.cancelDownload.setOnClickListener { app.updates.cancelDownload() }
        binding.environment.text = "环境：${BuildConfig.PDA_ENVIRONMENT.ifBlank { "未配置" }} · 渠道：${BuildConfig.PDA_UPDATE_CHANNEL}"
        renderCurrent()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            renderCurrent()
            if (!busy) restorePending()
        }
    }

    override fun onDestroy() {
        if (downloading) app.updates.cancelDownload()
        super.onDestroy()
    }

    private fun renderCurrent() {
        val info = packageManager.getPackageInfo(packageName, 0)
        binding.currentVersion.text = "当前版本：${info.versionName.orEmpty()}（${info.longVersionCode}）"
    }

    private fun restorePending() {
        val state = app.updates.savedState() ?: return
        binding.lastCheck.text = if (state.checkedAt > 0) "上次检查：${DisplayTime.formatEpochMillis(state.checkedAt)}" else "尚未完成更新检查"
        runCatching { UpdateManifestVerifier(BuildConfig.PDA_UPDATE_PUBLIC_KEY).verify(SignedUpdateManifest.parse(state.manifestJson)) }.onSuccess { found ->
            payload = found; renderRelease(found)
            if (state.state == "READY_TO_INSTALL") showInstall(found)
            if (state.state == "INSTALL_PENDING") execute { confirmInstalled(found) }
            if (state.state == "DOWNLOADING") binding.status.text = "上次下载已暂停，点击下载更新继续"
            if (state.state == "FAILED") binding.status.text = "上次更新未完成：${state.error}，可重新检查或下载"
            if (state.state != "INSTALL_PENDING" && packageManager.getPackageInfo(packageName, 0).longVersionCode >= found.versionCode) {
                binding.download.visibility = View.GONE; binding.install.visibility = View.GONE
                binding.status.text = "上次检查的版本已安装，可再次联网检查更新"
            }
        }.onFailure { binding.status.text = "本机更新记录暂不可用，请重新检查更新；原业务资料保留" }
    }

    private fun checkUpdate() = execute {
        binding.status.text = "正在检查可信更新清单"
        when (val result = withContext(Dispatchers.IO) { app.updates.check() }) {
            is UpdateCheckResult.Available -> { payload = result.payload; renderRelease(result.payload); binding.status.text = "发现可用更新，请确认版本说明后下载" }
            is UpdateCheckResult.Current -> {
                payload = null; binding.release.text = ""; binding.notes.text = ""
                binding.download.visibility = View.GONE; binding.install.visibility = View.GONE
                binding.status.text = "当前已是最新版本；本次检查已完成"
            }
        }
        binding.lastCheck.text = "上次检查：${DisplayTime.formatEpochMillis(System.currentTimeMillis())}"
    }

    private fun download() = execute {
        val target = checkNotNull(payload) { "请先检查更新" }
        binding.download.visibility = View.GONE; binding.progress.visibility = View.VISIBLE; binding.install.visibility = View.GONE
        downloading = true; binding.cancelDownload.visibility = View.VISIBLE
        try { withContext(Dispatchers.IO) { app.updates.download(target) { done, total ->
            runOnUiThread { binding.progress.max = 1000; binding.progress.progress = ((done * 1000 / total.coerceAtLeast(1)).coerceIn(0, 1000)).toInt(); binding.progressText.text = "已下载 ${done / 1024} / ${total / 1024} KB" }
        } } } finally {
            downloading = false; binding.cancelDownload.visibility = View.GONE
            binding.progress.visibility = View.GONE; binding.download.visibility = View.VISIBLE
        }
        binding.progress.visibility = View.GONE; showInstall(target); binding.status.text = "安装包已校验，可以在安全点安装"
    }

    private fun install() = execute {
        val target = checkNotNull(payload) { "请先检查更新" }
        val blocked = withContext(Dispatchers.IO) { app.updates.installBlockReason() }
        if (blocked != null) { binding.status.text = blocked; return@execute }
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            binding.status.text = "请在系统设置中允许本应用安装未知应用，返回后再次点击安装"
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return@execute
        }
        UpdateGate.beginInstall()
        var handedOff = false
        try {
            val file = withContext(Dispatchers.IO) { app.updates.readyFile(target) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            app.updates.markInstallPending(target)
            installer.launch(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            handedOff = true
        } finally { if (!handedOff) UpdateGate.endInstall() }
        binding.status.text = "已交给系统安装器，请完成系统确认；不要卸载或清除数据"
    }

    private suspend fun confirmInstalled(target: UpdatePayload) {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (info.longVersionCode >= target.versionCode) {
            withContext(Dispatchers.IO) { app.updates.verifyInstalledData() }
            binding.download.visibility = View.GONE; binding.install.visibility = View.GONE
            binding.status.text = "更新完成：${info.versionName}；设备身份与本机资料检查通过"
        }
        else {
            app.updates.markInstallReady(target)
            showInstall(target)
            binding.status.text = "系统安装尚未完成；旧版本仍可使用，可重试安装"
        }
    }

    private fun renderRelease(found: UpdatePayload) {
        binding.release.text = "可用版本：${found.versionName}（${found.versionCode}）\n大小：${found.sizeBytes / 1024} KB\n发布时间：${DisplayTime.formatDateTime(found.publishedAt)}"
        binding.notes.text = "更新说明：${found.releaseNotes}\n兼容范围：Android ${found.minAndroidApi}+，服务端 ${found.serverCompatibility}"
        binding.download.visibility = View.VISIBLE; binding.install.visibility = View.GONE
    }

    private fun showInstall(found: UpdatePayload) { payload = found; binding.download.visibility = View.GONE; binding.install.visibility = View.VISIBLE }

    private fun execute(action: suspend () -> Unit) {
        if (busy) return
        busy = true; listOf(binding.back, binding.check, binding.download, binding.install).forEach { it.isEnabled = false }
        lifecycleScope.launch {
            try { action() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                binding.status.text = error.message ?: "更新未完成，请保留资料并重试"
            }
            finally { busy = false; listOf(binding.back, binding.check, binding.download, binding.install).forEach { it.isEnabled = true } }
        }
    }
}
