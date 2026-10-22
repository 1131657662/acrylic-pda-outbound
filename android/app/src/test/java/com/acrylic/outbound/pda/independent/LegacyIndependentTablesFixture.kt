package com.acrylic.outbound.pda.independent

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase

/** Removes only empty future tables while constructing a synthetic pre-independent database. */
object LegacyIndependentTablesFixture {
    private val tables = listOf(
        "independent_guard", "independent_void", "independent_scan", "independent_capture_state",
        "independent_capture", "independent_device_state", "independent_task_event",
        "independent_task_state", "independent_task_line", "independent_task_request",
        "independent_task", "independent_sku_active", "independent_sku_decision",
        "independent_sku_import", "independent_sku_chunk", "independent_sku_original"
    )

    fun removeEmptyAdditions(db: SQLiteDatabase) {
        check(tables.all { DatabaseUtils.queryNumEntries(db, it) == 0L }) {
            "Cannot construct a legacy fixture from populated independent originals"
        }
        tables.forEach { db.execSQL("DROP TABLE $it") }
    }
}
