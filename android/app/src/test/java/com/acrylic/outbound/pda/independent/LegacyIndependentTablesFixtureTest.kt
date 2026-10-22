package com.acrylic.outbound.pda.independent

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LegacyIndependentTablesFixtureTest {
    @Test fun populatedIndependentOriginalsRejectLegacyConstructionBeforeAnyTableIsDropped() =
        runBlocking<Unit>(Dispatchers.IO) {
            IndependentScanFixture().use { f ->
                f.start(); f.save()
                f.task.sku.db.close()
                f.task.sku.context.openOrCreateDatabase("outbound-pda.db", 0, null).use(::assertProtected)
            }
        }

    private fun assertProtected(db: SQLiteDatabase) {
        val before = snapshot(db)
        assertThrows(IllegalStateException::class.java) {
            LegacyIndependentTablesFixture.removeEmptyAdditions(db)
        }
        assertEquals(before, snapshot(db))
        assertEquals(1L, DatabaseUtils.queryNumEntries(db, "independent_scan"))
        assertEquals(1L, DatabaseUtils.queryNumEntries(db, "independent_guard"))
    }

    private fun snapshot(db: SQLiteDatabase): Map<String, List<List<String?>>> =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", null).use { cursor ->
            val names = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            names.associateWith { rows(db, it) }
        }

    private fun rows(db: SQLiteDatabase, name: String): List<List<String?>> =
        db.rawQuery("SELECT * FROM \"$name\" ORDER BY rowid", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(values(cursor)) }
        }

    private fun values(cursor: android.database.Cursor): List<String?> =
        (0 until cursor.columnCount).map { index ->
            when (cursor.getType(index)) {
                android.database.Cursor.FIELD_TYPE_NULL -> null
                android.database.Cursor.FIELD_TYPE_BLOB ->
                    "blob:" + java.util.Base64.getEncoder().encodeToString(cursor.getBlob(index))
                else -> cursor.getType(index).toString() + ":" + cursor.getString(index)
            }
        }
}
