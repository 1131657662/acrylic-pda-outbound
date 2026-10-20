package com.acrylic.outbound.pda.ui

import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaTestApplication
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

/** Missing identity produces an unavailable card; no canceled stage/confirm/activation chain runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = PdaTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryClosedCardReadTest {
    private val app get() = ApplicationProvider.getApplicationContext<PdaTestApplication>()

    @After fun close() { app.database.close(); WorkManagerTestInitHelper.closeWorkDatabase() }

    @Test fun missingIdentityReturnsDisabledCardWithoutChangingAnyTable() {
        assertFalse(BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT)
        assertFalse(BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT)
        assertFalse(BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT)
        assertNull(app.session.saved())
        val before = counts()

        val card = TemporaryHandoverCardReader(app).read(UUID.randomUUID().toString())

        assertEquals("TEMP_DISABLED", card.action)
        assertFalse(card.actionAvailable)
        assertTrue(card.status.contains("原资料保留"))
        assertEquals(before, counts())
        assertNull(app.session.saved())
    }

    private fun counts(): Map<String, Long> {
        val db = app.database.openHelper.readableDatabase
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        return tables.associateWith { table ->
            check(table.matches(Regex("[A-Za-z0-9_]+")))
            db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                check(cursor.moveToFirst()); cursor.getLong(0)
            }
        }
    }
}
