package com.acrylic.m71demo.device

import org.json.JSONArray
import org.json.JSONObject

object KeyWizardJson {
    fun encode(session: KeyWizardSession, fingerprint: String, status: String): JSONObject = JSONObject()
        .put("schemaVersion", 1).put("runId", session.id).put("deviceFingerprint", fingerprint)
        .put("appVersion", com.acrylic.m71demo.BuildConfig.VERSION_NAME).put("status", status)
        .put("operatorConfirmedLabels", status == "APPLIED").put("recordedAt", System.currentTimeMillis())
        .put("scope", "KEY_EVENTS_AND_OPERATOR_LABELS_ONLY_NOT_FULL_DEVICE_ACCEPTANCE")
        .put("samples", JSONArray(session.samples.map { sample -> JSONObject()
            .put("role", sample.role.name).put("label", sample.role.label).put("down", stroke(sample.down))
            .put("up", stroke(sample.up)).put("repeats", sample.repeats) }))

    fun decode(json: JSONObject, fingerprint: String): KeyWizardSession {
        require(json.getInt("schemaVersion") == 1 && json.getString("deviceFingerprint") == fingerprint)
        val id = json.getString("runId")
        java.util.UUID.fromString(id)
        val rows = json.getJSONArray("samples")
        require(rows.length() <= KeyRole.entries.size * 2)
        val session = KeyWizardSession(id)
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val expected = KeyRole.entries[i / 2]
            require(row.getString("role") == expected.name)
            session.pause()
            session.accept(readStroke(row.getJSONObject("down")))
            require(session.waitingRelease)
            require(session.accept(readStroke(row.getJSONObject("up"))))
            session.samples[i] = session.samples[i].copy(repeats = row.getInt("repeats").also { require(it >= 0) })
        }
        session.pause()
        return session
    }

    fun stroke(s: KeyStroke): JSONObject = JSONObject().put("keyCode", s.keyCode).put("scanCode", s.scanCode)
        .put("deviceId", s.deviceId).put("source", s.source).put("action", s.action).put("repeatCount", s.repeatCount)
        .put("eventTime", s.eventTime).put("downTime", s.downTime).put("physical", s.physical)

    private fun readStroke(j: JSONObject) = KeyStroke(j.getInt("keyCode"), j.getInt("scanCode"),
        j.getInt("deviceId"), j.getInt("source"), j.getInt("action"), j.getInt("repeatCount"),
        j.getLong("eventTime"), j.getLong("downTime"), j.getBoolean("physical"))
}
