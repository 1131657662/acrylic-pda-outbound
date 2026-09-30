package com.acrylic.outbound.pda

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.evidence.LocalDraftQueueGuard
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * R06 A1.1 criteria 1, 2, 3 and 5 on a real device: the stopped-evidence rows and the paused re-upload queue must
 * survive a **real process death** (`am force-stop`) plus a cold start, byte for byte, and must not resume by
 * themselves.
 *
 * Two phases, driven by `.local/r06-a11-run.ps1`: `phase1` seeds and records, the script issues a real
 * `am force-stop`, and `phase2` runs in a fresh process and compares.
 *
 * <h2>What this proves, and what it must NOT be read as proving</h2>
 *
 * * **Proves**: the stop evidence (stop row, preserved originals, signature row), the synthetic
 *   `LOCAL_EVIDENCE_STOP` event count, and the paused queue state are **identical** after a genuine process death
 *   and cold start, and that the queue stays disabled.
 * * **Does NOT prove**: that the stopping action, the signature or the event were produced by the product. The
 *   dataset is raw-SQL synthetic ([R06A11SyntheticStopDataset]) and the signature row is a structural placeholder
 *   that is deliberately **not** a valid proof — no device-side signature verification is performed or claimed.
 *   Those halves are covered by the batch's real-Room unit tests.
 * * **Identity switch ([LocalDraftQueueGuard])**: the guard is driven with **hand-written session JSON objects**,
 *   so this exercises the guard's logic only. The **real login/pairing flow is NOT exercised**, and the guard is
 *   the product class, not a stub.
 * * **"0 POSTs to the peer" is NOT asserted**: no server is attached in this test, so nothing here observes the
 *   network. The claim made is the weaker, checkable one: automatic transfer stays disabled
 *   (`autoRetry=false`, state `ACTION_REQUIRED`).
 * * The synthetic event is inserted with `captureId = NULL`; it exists only so the "event count is unchanged"
 *   assertion has something to count.
 */
@RunWith(AndroidJUnit4::class)
class LocalDraftStopDurabilityDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as PdaApplication

    private val databaseName = "outbound-pda.db"
    private val markerFile = "r06a11-g1-before.json"
    private val orderId = R06A11SyntheticStopDataset.ORDER_ID

    @Before fun requireAuthorisedDevice() {
        assertTrue("device checks must run on an emulator", android.os.Build.MODEL.startsWith("sdk_gphone"))
        assertEquals("device checks must use the staging build", "com.acrylic.outbound.pda.staging", app.packageName)
        assertEquals("this class is only authorised on PdaDownloadVerify", "PdaDownloadVerify",
            InstrumentationRegistry.getArguments().getString("r06a11Avd"))
    }

    /** Phase 1: seed the stopped evidence, produce a paused queue, and record every fact we will compare later. */
    @Test fun phase1() {
        app.database.openHelper.writableDatabase // create the database before touching the file directly

        val path = context.getDatabasePath(databaseName).absolutePath
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            R06A11SyntheticStopDataset.seed(raw)
            insertSyntheticStopEvent(raw)
            // (C) step 1: `pauseAll` only touches rows with autoRetry=1, so the row must be enabled first.
            raw.execSQL("UPDATE local_draft_queue SET state='READY', autoRetry=1, error='' WHERE clientOrderId=?",
                arrayOf<Any?>(orderId))
            assertEquals("precondition: the queue row must be enabled before the identity switch", 1L,
                raw.rawQuery("SELECT autoRetry FROM local_draft_queue WHERE clientOrderId=?",
                    arrayOf(orderId)).use { it.moveToFirst(); it.getLong(0) })
        }

        // (C) step 2: the SAME field set the guard compares; jsonA and jsonB differ only in deviceId.
        // Hand-written input: this drives the guard's logic, it is NOT a real login/pairing transition.
        val jsonA = sessionJson("device-A")
        val jsonB = sessionJson("device-B")
        app.database.runInTransaction {
            LocalDraftQueueGuard(app.database).sessionChanged(jsonA, jsonB)
        }

        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            val queue = queueRow(raw)
            assertEquals("(C) the identity switch must pause the queue", "ACTION_REQUIRED",
                queue.getString("state"))
            assertEquals("(C) the identity switch must disable automatic transfer", 0L, queue.getLong("autoRetry"))

            val snapshot = snapshot(raw)
            File(context.filesDir, markerFile).writeText(snapshot.toString())
            context.getExternalFilesDir(null)?.let { File(it, markerFile).writeText(snapshot.toString()) }
            report(1, snapshot)
        }
    }

    /** Phase 2: a fresh process. Everything recorded in phase 1 must be byte-identical, and the queue stays paused. */
    @Test fun phase2() {
        val before = JSONObject(readMarker().readText())
        val path = context.getDatabasePath(databaseName).absolutePath
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            val after = snapshot(raw)

            // 1) stop row and signature row still present, same evidenceId, byte-identical
            assertEquals("stop row must survive the restart", before.getJSONObject("stop").toString(),
                after.getJSONObject("stop").toString())
            assertEquals("signature row must survive the restart", before.getJSONObject("signature").toString(),
                after.getJSONObject("signature").toString())
            assertEquals("stop evidenceId must be unchanged",
                before.getJSONObject("stop").getString("evidenceId"),
                after.getJSONObject("stop").getString("evidenceId"))
            assertEquals("signature evidenceId must match the surviving stop",
                after.getJSONObject("stop").getString("evidenceId"),
                after.getJSONObject("signature").getString("evidenceId"))
            assertTrue("the evidenceId must not be blank", after.getJSONObject("stop").getString("evidenceId").isNotEmpty())

            // 2) preserved originals: rawBytes byte-identical
            assertEquals("preserved originals must be byte-identical after the restart",
                before.getJSONArray("items").toString(), after.getJSONArray("items").toString())

            // 3) LOCAL_EVIDENCE_STOP event count unchanged (synthetic event, see the class comment)
            assertEquals("the LOCAL_EVIDENCE_STOP event count must not change",
                before.getLong("stopEventCount"), after.getLong("stopEventCount"))

            // 4) the queue must not have resumed
            assertEquals("the queue must not resume after a restart", before.getJSONObject("queue").toString(),
                after.getJSONObject("queue").toString())
            assertEquals("automatic transfer must stay disabled", 0L, after.getJSONObject("queue").getLong("autoRetry"))
            assertEquals("the queue must stay in ACTION_REQUIRED", "ACTION_REQUIRED",
                after.getJSONObject("queue").getString("state"))

            report(2, after)
        }

        // (C) step 3: returning to the ORIGINAL session must not silently re-enable automatic transfer.
        val jsonA = sessionJson("device-A")
        app.database.runInTransaction {
            LocalDraftQueueGuard(app.database).reconcile(jsonA)
        }
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            val queue = queueRow(raw)
            assertEquals("(C) reconcile must not re-enable automatic transfer", 0L, queue.getLong("autoRetry"))
            assertEquals("(C) the queue must still be ACTION_REQUIRED", "ACTION_REQUIRED", queue.getString("state"))
            println("R06A11_G1_RECONCILE state=${queue.getString("state")} autoRetry=${queue.getLong("autoRetry")}")
        }
        println("R06A11_G1_RESULT all four assertion groups passed in a fresh process")
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    /** The seven fields `LocalDraftQueueGuard` compares; only deviceId differs between A and B. */
    private fun sessionJson(deviceId: String) = JSONObject()
        .put("userId", R06A11SyntheticStopDataset.USER_ID)
        .put("deviceId", deviceId)
        .put("warehouseId", R06A11SyntheticStopDataset.WAREHOUSE_ID)
        .put("serverRecoveryEpoch", R06A11SyntheticStopDataset.RECOVERY_EPOCH)
        .put("localSessionId", "session-1")
        .put("userAuthVersion", R06A11SyntheticStopDataset.USER_AUTH_VERSION)
        .put("deviceVersion", R06A11SyntheticStopDataset.DEVICE_VERSION)

    /**
     * A synthetic stop event. `captureId` is NULL on purpose: it only exists to be counted.
     *
     * **Idempotent on purpose.** Emulator userdata persists between runs (`-no-snapshot-save` disables only
     * snapshot saving, not the userdata image), so a fixed primary key must be deleted before it is inserted
     * again: a second run would otherwise hit a UNIQUE violation - which is exactly how the repeat run failed.
     */
    private fun insertSyntheticStopEvent(db: SQLiteDatabase) {
        db.execSQL("DELETE FROM temporary_event WHERE id=?", arrayOf<Any?>("r06a11-event"))
        db.execSQL(
            "INSERT INTO temporary_event(id,clientOrderId,captureId,originDeviceId,operatorId,localSeq,type,detail," +
                "wallMs,elapsedMs,boot) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>("r06a11-event", orderId, null, R06A11SyntheticStopDataset.DEVICE_ID,
                R06A11SyntheticStopDataset.USER_ID, 1L, "LOCAL_EVIDENCE_STOP", "synthetic", 0L, 0L, 0)
        )
    }

    private fun queueRow(db: SQLiteDatabase): JSONObject =
        db.rawQuery("SELECT state, autoRetry, error FROM local_draft_queue WHERE clientOrderId=?",
            arrayOf(orderId)).use { cursor ->
            assertTrue("the queue row must exist", cursor.moveToFirst())
            JSONObject().put("state", cursor.getString(0)).put("autoRetry", cursor.getLong(1))
                .put("error", cursor.getString(2))
        }

    private fun snapshot(db: SQLiteDatabase): JSONObject {
        val stop = db.rawQuery("SELECT evidenceId, metadataJson, rootSha256, recordCount, totalBytes FROM " +
            "local_draft_stop WHERE clientOrderId=?", arrayOf(orderId)).use { cursor ->
            assertTrue("the stop row must exist", cursor.moveToFirst())
            JSONObject().put("evidenceId", cursor.getString(0)).put("metadataJson", cursor.getString(1))
                .put("rootSha256", cursor.getString(2)).put("recordCount", cursor.getLong(3))
                .put("totalBytes", cursor.getLong(4))
        }
        val signature = db.rawQuery("SELECT evidenceId, publicKey, proofJson FROM local_draft_stop_signature " +
            "WHERE clientOrderId=?", arrayOf(orderId)).use { cursor ->
            assertTrue("the signature row must exist", cursor.moveToFirst())
            JSONObject().put("evidenceId", cursor.getString(0)).put("publicKey", cursor.getString(1))
                .put("proofJson", cursor.getString(2))
        }
        val items = org.json.JSONArray()
        db.rawQuery("SELECT ordinal, bytesSha256, rawBytes FROM local_draft_stop_item WHERE clientOrderId=? " +
            "ORDER BY ordinal", arrayOf(orderId)).use { cursor ->
            while (cursor.moveToNext()) {
                items.put(JSONObject().put("ordinal", cursor.getLong(0)).put("bytesSha256", cursor.getString(1))
                    .put("rawBytes", R06A11SyntheticStopDataset.hex(cursor.getBlob(2))))
            }
        }
        val stopEvents = db.rawQuery("SELECT COUNT(*) FROM temporary_event WHERE clientOrderId=? AND " +
            "type='LOCAL_EVIDENCE_STOP'", arrayOf(orderId)).use { it.moveToFirst(); it.getLong(0) }
        assertTrue("the synthetic stop event must exist", stopEvents >= 1L)
        assertTrue("the preserved originals must exist", items.length() >= 1)

        return JSONObject().put("stop", stop).put("signature", signature).put("items", items)
            .put("stopEventCount", stopEvents).put("queue", queueRow(db))
    }

    private fun readMarker(): File {
        val file = File(context.filesDir, markerFile)
        assertNotNull("phase 1 must have written its marker; run phase1 first", file.takeIf { it.isFile })
        return file
    }

    private fun report(phase: Int, snapshot: JSONObject) {
        println("R06A11_G1 phase=$phase evidenceId=${snapshot.getJSONObject("stop").getString("evidenceId")}")
        println("R06A11_G1 phase=$phase items=${snapshot.getJSONArray("items").length()} " +
            "stopEvents=${snapshot.getLong("stopEventCount")} queueState=${snapshot.getJSONObject("queue").getString("state")} " +
            "autoRetry=${snapshot.getJSONObject("queue").getLong("autoRetry")}")
    }
}
