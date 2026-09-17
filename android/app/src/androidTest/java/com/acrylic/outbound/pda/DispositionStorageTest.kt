package com.acrylic.outbound.pda

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.continuation.LocalContinuation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DispositionStorageTest {
    @Test fun largeSignedContinuationTicketSurvivesCursorWindowAndDatabaseReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext; val name = "disposition-storage-${UUID.randomUUID()}.db"
        val ticket = "核验卸货📦原序号".repeat(180000); assertTrue(ticket.toByteArray().size > 2 * 1024 * 1024)
        val row = LocalContinuation("ticket", "assignment", "{}", ticket, 2, 3, "recall")
        try {
            val db = Room.databaseBuilder(context, PdaDatabase::class.java, name).build()
            try { db.continuations().insert(row); assertEquals(row, db.continuations().get(row.id)) } finally { db.close() }
            val reopened = Room.databaseBuilder(context, PdaDatabase::class.java, name).build()
            try { assertEquals(row, reopened.continuations().get(row.id)); assertEquals(2L, reopened.continuations().current(row.assignmentId)!!.workRound) }
            finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
