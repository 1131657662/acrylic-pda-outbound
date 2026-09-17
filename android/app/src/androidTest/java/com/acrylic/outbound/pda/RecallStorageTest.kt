package com.acrylic.outbound.pda

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.recall.LocalRecall
import com.acrylic.outbound.pda.security.JwtVerifier
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecallStorageTest {
    @Test fun recoveryLargerThanCursorWindowRemainsExactAfterReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext; val name = "recall-storage-${UUID.randomUUID()}.db"
        val payload = "{\"evidence\":\"" + "原机停止📦保留原序号".repeat(180000) + "\"}"
        assertTrue(payload.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024)
        val row = LocalRecall("request", "assignment", "{}", "package", payload, JwtVerifier.sha(payload.toByteArray(Charsets.UTF_8)), "RECOVERY_FROZEN")
        try {
            val db = Room.databaseBuilder(context, PdaDatabase::class.java, name).build()
            try { db.recalls().insert(row); assertEquals(row, db.recalls().get(row.assignmentId)) } finally { db.close() }
            val reopened = Room.databaseBuilder(context, PdaDatabase::class.java, name).build()
            try { assertEquals(row, reopened.recalls().get(row.assignmentId)); assertNull(reopened.recalls().header(row.assignmentId)!!.payload) }
            finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
