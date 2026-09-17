package com.acrylic.outbound.pda.preparation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalActivationTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var db: PdaDatabase
    private lateinit var context: Context
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext(); context.deleteDatabase("outbound-pda.db")
        db = PdaDatabase.open(context)
    }
    @After fun close() { db.close(); context.deleteDatabase("outbound-pda.db") }

    @Test fun realServerPackageActivatesAtomicallyAndSurvivesDatabaseReopen() = io {
        val input = ContractFixture.input(folder.root)
        val order = LocalActivation(db).activate(input)
        assertEquals(2, order.rowCount); assertNull(order.ackAt)
        assertEquals(2, db.preparations().count(order.assignmentId))
        db.close(); db = PdaDatabase.open(context)
        assertEquals(order, db.preparations().order(order.assignmentId))
        assertEquals(order, LocalActivation(db).activate(input))
    }

    @Test fun tamperedFileDoesNotCreateReadyOrder() = io {
        val input = ContractFixture.input(folder.root)
        val broken = input.copy(payloads = input.payloads + ("inventory" to "[]".toByteArray()))
        assertThrows(Exception::class.java) { LocalActivation(db).activate(broken) }
        assertTrue(db.preparations().orders().isEmpty())
    }

    @Test fun insertFailureRollsBackHeaderAndEveryBaleThenOriginalPackageCanRetry() = io {
        val input = ContractFixture.input(folder.root)
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER test_abort BEFORE INSERT ON prepared_bale
            WHEN (SELECT COUNT(*) FROM prepared_bale)>0 BEGIN SELECT RAISE(ABORT,'test interruption'); END""")
        assertThrows(Exception::class.java) { LocalActivation(db).activate(input) }
        assertTrue(db.preparations().orders().isEmpty())
        assertEquals(0, db.preparations().count(input.assignment.getString("id")))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER test_abort")
        assertEquals(2, LocalActivation(db).activate(input).rowCount)
    }

    @Test fun failedRedownloadKeepsPreviousCompleteVersion() = io {
        val input = ContractFixture.input(folder.root); val original = LocalActivation(db).activate(input)
        assertThrows(Exception::class.java) { LocalActivation(db).activate(input.copy(payloads = input.payloads - "availability")) }
        assertEquals(original, db.preparations().order(original.assignmentId))
        assertEquals(2, db.preparations().count(original.assignmentId))
    }

    @Test fun expiredOrOtherPersonsAuthorizationCannotActivate() = io {
        val input = ContractFixture.input(folder.root)
        assertThrows(Exception::class.java) { LocalActivation(db).activate(input.copy(now = input.now.plusSeconds(172800))) }
        val other = JSONObject(input.session.toString()).put("userId", java.util.UUID.randomUUID().toString())
        assertThrows(Exception::class.java) { LocalActivation(db).activate(input.copy(session = other)) }
        assertTrue(db.preparations().orders().isEmpty())
    }

    @Test fun uncertainClaimKeepsOriginalBodyAndKeyAcrossRestart() = io {
        val intent = ClaimIntent("order", "user", "device", "original-key", "original-body", "1")
        db.preparations().insertIntent(intent); db.close(); db = PdaDatabase.open(context)
        assertEquals(intent, db.preparations().intent("order"))
        db.preparations().resolveIntent("order", "other-user", "device")
        assertEquals(intent, db.preparations().intent("order"))
    }
    private fun io(block: () -> Unit) = runBlocking<Unit>(Dispatchers.IO) { block() }
}
