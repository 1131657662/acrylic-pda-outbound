package com.acrylic.outbound.pda

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fixture setup only; never counted as migration, integrity, or recovery acceptance. */
class R06A11DevicePreconditionTest {
    @Test fun primeMigrationRows() {
        R06A11DeviceGuard.require("PdaUpgradeVerify")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as PdaApplication
        app.database.openHelper.writableDatabase
        app.database.close()
        val path = context.getDatabasePath("outbound-pda.db").absolutePath
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val first = R06A11SyntheticStopDataset.seed(db)
            val second = R06A11SyntheticStopDataset.seed(db)
            assertEquals("Setup must be idempotent", first.toString(), second.toString())
            val key = arrayOf(R06A11SyntheticStopDataset.ORDER_ID)
            db.rawQuery("SELECT COUNT(*) FROM local_draft_stop WHERE clientOrderId=?", key).use {
                it.moveToFirst(); assertEquals(1L, it.getLong(0))
            }
            db.rawQuery("SELECT COUNT(*) FROM local_draft_stop_item WHERE clientOrderId=?", key).use {
                it.moveToFirst(); assertEquals(2L, it.getLong(0))
            }
            println("R06A11_SETUP synthetic seed repeated; ownStop=1 ownItems=2; not acceptance")
        }
    }

    @Test fun prepareRecoveryFixtureDirectory() {
        R06A11DeviceGuard.require("PdaDownloadVerify")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        (context.applicationContext as PdaApplication).database.openHelper.writableDatabase
        checkNotNull(context.getExternalFilesDir(null)).let { check(it.isDirectory) }
        println("R06A11_SETUP recovery directory ready; no business rows seeded")
    }
}