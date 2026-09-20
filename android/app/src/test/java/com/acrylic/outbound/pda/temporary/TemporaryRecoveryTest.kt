package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.LocalRecoveryControl
import com.acrylic.outbound.pda.sync.RecoveryProtection
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryRecoveryTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun lostIssueResponseReusesPersistedKeyAndBodyAfterReopen() = io {
        TemporaryFixture(folder.root).use { f ->
            f.beforeCall = { if (it.path == "pda/temporary-preparations") throw IOException("response lost") }
            assertThrows(IOException::class.java) { f.repository.prepare {} }
            val first = f.calls.last(); val saved = f.repository.local()!!; assertNull(saved.receiptJson)
            f.reopen(); f.beforeCall = {}; f.repository.prepare {}
            val retried = f.calls.last { it.path == first.path }
            assertEquals(first.key, retried.key); assertEquals(first.body.toString(), retried.body.toString())
            assertEquals(saved.key, f.repository.local()!!.key)
        }
    }
    @Test fun partialDownloadResumesOnlyMissingPartsAcrossReopen() = io {
        TemporaryFixture(folder.root).use { f ->
            f.beforeDownload = { if (it == "policy") throw IOException("interrupted") }
            assertThrows(IOException::class.java) { f.repository.prepare {} }
            val request = f.repository.local()!!; assertNull(f.repository.prepared(request))
            f.reopen(); f.beforeDownload = {}; f.repository.prepare {}
            assertEquals(1, f.downloads.count { it == "inventory" }); assertEquals(1, f.downloads.count { it == "mapping" })
            assertEquals(2, f.downloads.count { it == "policy" }); assertNotNull(f.repository.prepared(request))
        }
    }
    @Test fun lostAckResponseRetainsOriginalDeclarationAndReplaysAfterExpiry() = io {
        TemporaryFixture(folder.root).use { f ->
            f.beforeCall = { if (it.path.endsWith("download-ack")) throw IOException("ack lost") }
            assertThrows(IOException::class.java) { f.repository.prepare {} }
            val request = f.repository.local()!!; val pending = f.repository.prepared(request)!!
            assertNull(pending.ackJson); f.reopen(); f.beforeCall = {}; f.time = f.time.plusSeconds(172800)
            val acknowledged = f.repository.prepare {}
            assertEquals(pending, acknowledged.copy(ackJson = null)); assertNotNull(acknowledged.ackJson)
            val attempts = f.calls.filter { it.path.endsWith("download-ack") }
            assertEquals(attempts[0].key, attempts[1].key); assertEquals(attempts[0].body.toString(), attempts[1].body.toString())
            assertTrue(f.repository.readiness(request).contains("到期"))
        }
    }
    @Test fun invalidAckCannotClearPendingOrReplaceOriginalDeclaration() = io {
        TemporaryFixture(folder.root).use { f ->
            val valid = f.ack; f.ack = JSONObject(valid.toString()).put("signedAck", f.receipt.getString("signedManifest"))
            assertThrows(Exception::class.java) { f.repository.prepare {} }
            val row = f.repository.prepared(f.repository.local()!!)!!; assertNull(row.ackJson)
            f.reopen(); f.ack = valid
            assertEquals(row, f.repository.prepare {}.copy(ackJson = null))
        }
    }
    @Test fun accountLogoutLoginVersionAndRecoveryChangesRejectLateActivation() = io {
        for (change in listOf("logout", "localSessionId", "userAuthVersion", "deviceVersion", "recovery")) {
            TemporaryFixture(folder.newFolder()).use { f ->
                f.beforeDownload = { if (it == "availability") when (change) {
                    "logout" -> f.auth = null
                    "recovery" -> f.db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "1", mode = "RECONCILING"))
                    else -> f.auth!!.put(change, "changed")
                } }
                assertThrows(Exception::class.java) { f.repository.prepare {} }
                f.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM temporary_preparation").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            }
        }
    }
    @Test fun sessionChangeWhileAcknowledgingKeepsPendingProof() = io {
        TemporaryFixture(folder.root).use { f ->
            f.beforeCall = { if (it.path.endsWith("download-ack")) f.auth!!.put("localSessionId", "new-login") }
            assertThrows(Exception::class.java) { f.repository.prepare {} }
            assertNull(f.repository.prepared(f.repository.local()!!)!!.ackJson)
            f.beforeCall = {}; assertNotNull(f.repository.prepare {}.ackJson)
        }
    }
    @Test fun logoutBeforeIssueResponseLeavesOnlyOriginalRequestForExplicitRecovery() = io {
        TemporaryFixture(folder.root).use { f ->
            val auth = JSONObject(f.auth!!.toString())
            f.beforeCall = { if (it.path == "pda/temporary-preparations") f.auth = null }
            assertThrows(Exception::class.java) { f.repository.prepare {} }
            val key = f.calls.last().key!!; assertNull(f.db.temporary().request(key)!!.receiptJson)
            f.reopen(); f.auth = auth.put("localSessionId", "new-login"); f.beforeCall = {}
            assertEquals(key, f.repository.prepare {}.requestKey)
        }
    }
    @Test fun newRecoveryEpochWithoutControlStillDetectsHistoricalTemporaryRequest() = io {
        TemporaryFixture(folder.root).use { f ->
            val row = f.saveRequest()
            RecoveryProtection(f.db).observe(JSONObject().put("serverRecoveryEpoch", "2").put("serviceMode", "NORMAL"))
            assertEquals("RECONCILING", f.db.sync().recovery()!!.mode); assertEquals(row, f.db.temporary().request(row.key))
            RecoveryProtection(f.db).observe(JSONObject().put("serverRecoveryEpoch", "2").put("serviceMode", "NORMAL"), "2")
            assertThrows(Exception::class.java) { f.repository.prepare {} }
        }
    }
    private fun io(block: () -> Unit) = runBlocking<Unit>(Dispatchers.IO) { block() }
}
