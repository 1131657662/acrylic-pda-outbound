package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Preserve a failed fixture before inherited cleanup; never label a regenerated DB as the original failure. */
internal object TemporaryDetailsFailure {
    fun preserve(db: PdaDatabase, error: Throwable) {
        val root = System.getenv("PDA_TEMPORARY_DETAILS_FAILURE") ?: return
        val target = File(root, UUID.randomUUID().toString()).apply { check(!exists()); mkdirs() }
        File(target, "failure.txt").writeText(error.stackTraceToString())
        db.openHelper.writableDatabase.query("VACUUM INTO ?", arrayOf(File(target, "failed.sqlite").path)).use { it.moveToFirst() }
        File(target, "identity.json").writeText(JSONObject().put("syntheticOnly", true).put("preservedBeforeTeardown", true).toString())
    }
}
