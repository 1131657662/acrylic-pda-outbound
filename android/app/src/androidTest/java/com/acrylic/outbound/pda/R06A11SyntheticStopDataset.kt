package com.acrylic.outbound.pda

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.security.MessageDigest

/**
 * R06 A1.1 — the synthetic **stop-side dataset** shared by G4-3 (`LocalDraftStopUpgradeDeviceTest`) and G1
 * (`LocalDraftStopDurabilityDeviceTest`).
 *
 * <h2>Why this exists and what it is honestly worth</h2>
 *
 * Creating a stopped temporary order through the product needs a **server-signed temporary authorisation package**
 * plus a paired session (`TemporaryWorkStore` + `TemporaryConnection.localDraftStopKey()`/`signLocalDraftStop`),
 * and those live only in the unit-test source set. A device test therefore cannot drive the product path, so this
 * helper inserts the rows it needs **with raw SQL**, in foreign-key order, into:
 *
 * ```
 * temporary_request(key)
 *   -> temporary_preparation(requestKey)
 *     -> temporary_draft(authorizationId)
 *       -> local_draft_stop(clientOrderId) -> local_draft_stop_item / local_draft_stop_signature
 *         -> local_draft_transfer(clientOrderId, FK -> signature)
 *           -> local_draft_queue(clientOrderId, FK -> transfer)
 * ```
 *
 * **Division of proof (must not be misread):**
 * * **device layer (this helper)** proves only that these rows are **byte-for-byte unchanged** across a real
 *   `am force-stop` + cold start and across a real Room migration — i.e. row-level durability and immutability.
 * * **"the stopping action is produced by the product"** and **"the stop signature is valid"** are covered by the
 *   batch's real-Room unit tests (`LocalDraftStopTest`, `LocalDraftStopSignatureTest`, `LocalDraftStopIntegrityTest`,
 *   `LocalDraftStopWriterTest`, `LocalDraftSourceBindingMigrationTest`).
 * * The signature row inserted here is a **structural placeholder**: it is deliberately **not** a valid proof, and
 *   no test may claim that device-side signature verification passed.
 * * These are synthetic rows, never server-issued facts, and they are never uploaded.
 *
 * Not seeded here (deliberately): `temporary_capture` / `temporary_scan` / `temporary_event`. The stopped-state
 * assertions in scope never read them, and their full column sets were not needed to make this correct.
 */
internal object R06A11SyntheticStopDataset {
    const val REQUEST_KEY = "r06a11-request"
    const val AUTHORIZATION_ID = "r06a11-authorization"
    const val ORDER_ID = "r06a11-order"
    const val EVIDENCE_ID = "r06a11-evidence"
    const val DEVICE_ID = "r06a11-device"
    const val USER_ID = "r06a11-user"
    const val WAREHOUSE_ID = "r06a11-warehouse"
    const val RECOVERY_EPOCH = "1"
    const val USER_AUTH_VERSION = "1"
    const val DEVICE_VERSION = "1"

    /** The tables this helper writes, in FK order. Used for the per-table fingerprint comparisons. */
    val SEEDED_TABLES = listOf(
        "temporary_request", "temporary_preparation", "temporary_draft",
        "local_draft_stop", "local_draft_stop_item", "local_draft_stop_signature",
        "local_draft_transfer", "local_draft_queue"
    )

    /** Inserts the whole chain in one transaction. Idempotent: any previous seed for the same ids is removed first. */
    fun seed(db: SQLiteDatabase): JSONObject {
        db.beginTransaction()
        try {
            delete(db)

            // --- temporary_request: the server-signed request identity the draft hangs off ---
            db.execSQL(
                "INSERT INTO temporary_request(key,userId,deviceId,warehouseId,recoveryEpoch,userAuthVersion," +
                    "deviceVersion,bodyJson,signingKeyJson,receiptJson,previousKey) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(REQUEST_KEY, USER_ID, DEVICE_ID, WAREHOUSE_ID, RECOVERY_EPOCH, USER_AUTH_VERSION,
                    DEVICE_VERSION, "{\"synthetic\":true}", "{\"synthetic\":true}", null, null)
            )
            db.execSQL(
                "INSERT INTO temporary_preparation(authorizationId,requestKey,bundleId,manifestSha256,activatedAt," +
                    "inventoryRows,availabilityRows,ackKey,ackBodyJson,ackJson) VALUES(?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(AUTHORIZATION_ID, REQUEST_KEY, "r06a11-bundle", sha256("manifest"), "2026-09-30T00:00:00Z",
                    1, 1, "r06a11-ack", "{\"synthetic\":true}", null)
            )
            db.execSQL(
                "INSERT INTO temporary_draft(clientOrderId,creatorDeviceId,operatorId,warehouseId,authorizationId," +
                    "temporaryNo,createdAt,vehicleNo,containerNo,reason,revision,state,nextScanSeq,planSha256) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(ORDER_ID, DEVICE_ID, USER_ID, WAREHOUSE_ID, AUTHORIZATION_ID, "TMP-R06A11",
                    "2026-09-30T00:00:00Z", "沪A00000", "", "", 0L, "EVIDENCE_STOPPED", 1L, "")
            )

            // --- the stop evidence itself, its preserved originals and its (placeholder) signature ---
            db.execSQL(
                "INSERT INTO local_draft_stop(clientOrderId,evidenceId,metadataJson,rootSha256,recordCount,totalBytes)" +
                    " VALUES(?,?,?,?,?,?)",
                arrayOf<Any?>(ORDER_ID, EVIDENCE_ID, metadata(), sha256("root"), 2L, 16L)
            )
            val payload = ByteArray(8) { it.toByte() }
            for (ordinal in 1L..2L) {
                db.execSQL(
                    "INSERT INTO local_draft_stop_item(clientOrderId,ordinal,kind,recordId,deviceSeq,recordStatus," +
                        "bytesSha256,rawBytes) VALUES(?,?,?,?,?,?,?,?)",
                    arrayOf<Any?>(ORDER_ID, ordinal, "CAPTURE", "r06a11-record-$ordinal", ordinal, "RECEIVED",
                        sha256("bytes-$ordinal"), payload)
                )
            }
            db.execSQL(
                "INSERT INTO local_draft_stop_signature(clientOrderId,evidenceId,publicKey,proofJson) VALUES(?,?,?,?)",
                arrayOf<Any?>(ORDER_ID, EVIDENCE_ID, "r06a11-placeholder-key", "{\"synthetic\":true}")
            )

            // --- transfer + queue, deliberately left PAUSED so the migration must not resurrect it ---
            db.execSQL(
                "INSERT INTO local_draft_transfer(clientOrderId,evidenceId,requestCount,requestSetSha256) VALUES(?,?,?,?)",
                arrayOf<Any?>(ORDER_ID, EVIDENCE_ID, 1L, sha256("requests"))
            )
            db.execSQL(
                "INSERT INTO local_draft_queue(clientOrderId,state,verifiedRequests,totalRequests,autoRetry,error) " +
                    "VALUES(?,?,?,?,?,?)",
                arrayOf<Any?>(ORDER_ID, "ACTION_REQUIRED", 0L, 1L, 0L, "r06a11 synthetic pause")
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val counts = JSONObject()
        for (name in SEEDED_TABLES) counts.put(name, count(db, name))
        return counts
    }

    fun delete(db: SQLiteDatabase) {
        // reverse FK order
        for (name in SEEDED_TABLES.reversed()) {
            db.execSQL("DELETE FROM `$name` WHERE " + keyColumn(name) + "=?", arrayOf<Any?>(keyValue(name)))
        }
    }

    private fun keyColumn(table: String) = when (table) {
        "temporary_request" -> "key"
        "temporary_preparation" -> "authorizationId"
        "local_draft_stop_item" -> "clientOrderId"
        else -> "clientOrderId"
    }

    private fun keyValue(table: String) = when (table) {
        "temporary_request" -> REQUEST_KEY
        "temporary_preparation" -> AUTHORIZATION_ID
        else -> ORDER_ID
    }

    private fun metadata() = JSONObject()
        .put("captureHighWatermark", "2")
        .put("auditThroughSeq", "2")
        .put("draft", JSONObject().put("clientOrderId", ORDER_ID).put("state", "EVIDENCE_STOPPED"))
        .toString()

    // ---- shared fingerprinting ------------------------------------------------------------------------------

    /** Every table name in the database except Room's own bookkeeping. */
    fun tableNames(db: SQLiteDatabase): List<String> =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
            "AND name<>'room_master_table' ORDER BY name", null).use { cursor ->
            val names = mutableListOf<String>()
            while (cursor.moveToNext()) names.add(cursor.getString(0))
            names
        }

    fun count(db: SQLiteDatabase, table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { it.moveToFirst(); it.getLong(0) }

    /** `count` + sha256 over the sorted canonical rows, per table. */
    fun fingerprint(db: SQLiteDatabase, exclude: Set<String> = emptySet()): JSONObject {
        val result = JSONObject()
        for (name in tableNames(db)) {
            if (name in exclude) continue
            val rows = mutableListOf<String>()
            db.rawQuery("SELECT * FROM `$name`", null).use { cursor ->
                while (cursor.moveToNext()) rows.add(canonicalRow(cursor))
            }
            result.put(name, JSONObject().put("count", rows.size).put("sha256", sha256(rows.sorted())))
        }
        return result
    }

    private fun canonicalRow(cursor: Cursor): String {
        val text = StringBuilder()
        for (index in 0 until cursor.columnCount) {
            when (cursor.getType(index)) {
                Cursor.FIELD_TYPE_NULL -> text.append("\u0000N")
                Cursor.FIELD_TYPE_INTEGER -> text.append("\u0000I").append(cursor.getLong(index))
                Cursor.FIELD_TYPE_FLOAT -> text.append("\u0000F").append(cursor.getDouble(index))
                Cursor.FIELD_TYPE_BLOB -> text.append("\u0000B").append(hex(cursor.getBlob(index)))
                else -> text.append("\u0000S").append(cursor.getString(index))
            }
            text.append('\u0001')
        }
        return text.toString()
    }

    fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) out.append("%02x".format(byte))
        return out.toString()
    }

    fun sha256(value: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    /** sha256 over newline-joined lines - the row-set digest used by [fingerprint]. */
    fun sha256(lines: List<String>): String = sha256(lines.joinToString("\n"))
}
