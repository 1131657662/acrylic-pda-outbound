package com.acrylic.outbound.pda

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.submission.LocalSubmission
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.work.WorkOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SubmissionStorageTest {
    @Test fun frozenBodyLargerThanCursorWindowSurvivesReopenByteForByte() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "submission-storage-${UUID.randomUUID()}.db"
        val payload = "{\"evidence\":\"" + "保存📦原序号".repeat(180000) + "\"}"
        val bytes = payload.toByteArray(Charsets.UTF_8)
        assertTrue(bytes.size > 2 * 1024 * 1024)
        val row = LocalSubmission("submission", "assignment", payload, JwtVerifier.sha(bytes))
        try {
            val db = Room.databaseBuilder(context, PdaDatabase::class.java, name).build()
            try {
                db.preparations().insertOrder(PreparedOrder("assignment", "order", "user", "device", "bundle", "hash",
                    "{}", "{}", "{}", "{}", "signed", "time", 1))
                db.work().insertOrder(WorkOrder("assignment", "order", "test", 1, 2, 1, 1000, "time", "FROZEN"))
                db.submissions().insert(row)
                assertEquals(row, db.submissions().get(row.id))
            } finally { db.close() }
            val reopened = Room.databaseBuilder(context, PdaDatabase::class.java, name).build()
            try {
                assertEquals(row, reopened.submissions().forOrder(row.assignmentId))
                reopened.submissions().progress(row.id, "RESULT_UNKNOWN", null, "重试", true)
                assertEquals(row.sha256, JwtVerifier.sha(reopened.submissions().get(row.id)!!.payload.toByteArray(Charsets.UTF_8)))
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
