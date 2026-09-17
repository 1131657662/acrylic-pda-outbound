package com.acrylic.m71demo

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.acrylic.m71demo.data.FullSyncOpenHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class CorruptionRetentionTest {
    @Test fun corruptFileIsRetainedForDiagnosisInsteadOfRecreated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "corrupt-${java.util.UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val original = ByteArray(4096) { 65 }
        file.writeBytes(original)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) { fail("Corrupt evidence was recreated") }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        val helper = FullSyncOpenHelper(configuration)
        try {
            assertThrows(android.database.sqlite.SQLiteException::class.java) { helper.writableDatabase }
            assertTrue(file.isFile)
            assertArrayEquals(original, file.readBytes())
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
