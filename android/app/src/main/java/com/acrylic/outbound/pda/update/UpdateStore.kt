package com.acrylic.outbound.pda.update

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

class UpdateStore(context: Context) {
    // The update is private app data. Keep it under files/ because FileProvider's
    // files-path can expose this directory without granting a broader root path.
    private val root = File(context.filesDir, "updates").apply { mkdirs() }
    private val stateFile = AtomicFile(File(root, "state.json"))

    @Synchronized fun read(): UpdateDownloadState? {
        if (!stateFile.baseFile.exists() && !File(stateFile.baseFile.path + ".bak").exists()) return null
        return runCatching { UpdateDownloadState.fromJson(JSONObject(String(stateFile.readFully(), Charsets.UTF_8))) }.getOrNull()
    }

    @Synchronized fun save(state: UpdateDownloadState) {
        val bytes = state.toJson().toString().toByteArray(Charsets.UTF_8)
        val output = stateFile.startWrite()
        try { output.write(bytes); stateFile.finishWrite(output) }
        catch (error: Exception) { stateFile.failWrite(output); throw error }
    }

    fun temp(releaseId: String) = File(root, "$releaseId.apk.part")
    fun apk(releaseId: String) = File(root, "$releaseId.apk")

    @Synchronized fun clearInvalid(releaseId: String) {
        temp(releaseId).delete(); apk(releaseId).delete()
        val current = read() ?: return
        if (current.releaseId == releaseId) save(current.copy(bytesDownloaded = 0, state = "FAILED", error = "下载文件已清理"))
    }

    fun availableBytes(): Long = root.usableSpace
}
