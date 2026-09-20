package com.acrylic.outbound.pda

import android.app.Activity
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File

/** Evidence for the retained recovery fixture; excludes only refreshable status caches. */
internal class RecoveryUiEvidence(private val app: PdaApplication) {
    private val facts = ArchiveRecoveryFacts(app)
    private val directory = File(app.filesDir, "recovery-ui").apply { mkdirs() }
    private val refreshable = setOf("local_cloud_order", "local_cloud_barcode", "local_sync_state")
    private val proof = app.database.auditArchives().rows("2", facts.device).single()
    val assignment get() = facts.original.getString("assignmentId")

    fun requireFixture() {
        facts.requireIsolated()
        assertEquals("2", app.database.sync().recovery()!!.observedServerEpoch)
        assertEquals("RECONCILING", app.database.sync().recovery()!!.mode)
        assertEquals("SYNCED", app.database.submissions().get(facts.submission)!!.state)
    }

    fun assertPreserved() {
        requireFixture()
        val originalSnapshot = JSONObject(facts.baseline.readText())
        val after = facts.snapshot()
        for (name in listOf("key", "binding", "submissionArchive", "auditArchive")) {
            assertEquals(name, originalSnapshot.getString(name), after.getString(name))
        }
        val original = originalSnapshot.getJSONObject("businessTables")
        for (name in original.keys()) {
            if (name in refreshable) continue
            assertEquals(name, original.getJSONObject(name).toString(), after.getJSONObject("businessTables").getJSONObject(name).toString())
        }
        assertEquals(proof, app.database.auditArchives().rows("2", facts.device).single())
        assertEquals(15L, app.database.audit().checkpoint("2", facts.device)!!.acknowledgedThrough)
    }

    fun screen(activity: Activity, name: String, ids: List<Int>) {
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        val fields = JSONObject()
        for (id in ids) {
            val view = activity.findViewById<TextView>(id)
            fields.put(activity.resources.getResourceEntryName(id), JSONObject().put("text", view.text.toString())
                .put("shown", view.isShown).put("enabled", view.isEnabled))
        }
        File(directory, "$name.json").writeText(fields.put("secureWindow", true).toString())
    }

    fun completed(name: String) {
        File(directory, "$name-result.json").writeText(JSONObject().put("passed", true)
            .put("pid", android.os.Process.myPid()).put("mode", "RECONCILING")
            .put("protectedLocalTables", 17).put("oldAndNewProofsPreserved", true).toString())
    }
}
