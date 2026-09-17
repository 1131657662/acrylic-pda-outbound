package com.acrylic.m71demo.device

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

class KeyCalibrationStore(context: Context, private val fingerprint: String = Build.FINGERPRINT) {
    private val directory = File(context.filesDir, "key-calibration").apply { check(mkdirs() || isDirectory) }
    private val draft = AtomicFile(File(directory, "draft.json"))
    private val active = AtomicFile(File(directory, "active.json"))
    private val reports = File(context.getExternalFilesDir(null) ?: context.filesDir, "evidence/key-tests")

    fun loadDraft(): KeyWizardSession? = if (exists(draft)) decode(draft) else null

    fun activeProfile(): KeyWizardSession? {
        if (!exists(active)) return null
        val json = read(active)
        require(json.getString("status") == "APPLIED" && json.getBoolean("operatorConfirmedLabels"))
        return KeyWizardJson.decode(json, fingerprint).also { require(it.complete) }
    }

    fun saveDraft(session: KeyWizardSession) = write(draft, KeyWizardJson.encode(session, fingerprint, "DRAFT"))

    fun archive(session: KeyWizardSession, status: String): String {
        require(status in setOf("COLLECTED", "ABANDONED", "APPLIED"))
        check(reports.mkdirs() || reports.isDirectory)
        val file = File(reports, "${session.id}-${java.util.UUID.randomUUID()}-$status.json")
        check(!file.exists())
        write(AtomicFile(file), KeyWizardJson.encode(session, fingerprint, status))
        return file.absolutePath
    }

    fun apply(session: KeyWizardSession): String {
        require(session.complete)
        // Validate persisted content with the same rules as a restored session before activation.
        val document = KeyWizardJson.encode(session, fingerprint, "APPLIED")
        require(KeyWizardJson.decode(document, fingerprint).complete)
        archive(session, "COLLECTED")
        write(active, document)
        return active.baseFile.absolutePath
    }

    private fun decode(file: AtomicFile) = KeyWizardJson.decode(read(file), fingerprint)
    private fun exists(file: AtomicFile) = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
    private fun read(file: AtomicFile) = JSONObject(String(file.readFully(), Charsets.UTF_8))

    private fun write(file: AtomicFile, json: JSONObject) {
        val stream = file.startWrite()
        try { stream.write(json.toString(2).toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
