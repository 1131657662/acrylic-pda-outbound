package com.acrylic.outbound.pda.evidence

import androidx.room.*

/** Explicit transport plan, separate from the signature made in the original stop transaction. */
@Entity(tableName = "local_draft_transfer", foreignKeys = [ForeignKey(entity = LocalDraftStopSignature::class,
    parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"])])
data class LocalDraftTransfer(@PrimaryKey val clientOrderId: String, val evidenceId: String,
    val requestCount: Long, val requestSetSha256: String)

@Entity(tableName = "local_draft_request", primaryKeys = ["clientOrderId", "position"],
    indices = [Index(value = ["originalKey"], unique = true)],
    foreignKeys = [ForeignKey(entity = LocalDraftStopSignature::class, parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"])])
data class LocalDraftRequest(val clientOrderId: String, val position: Long, val operation: String,
    val originalKey: String, val requestJson: String, val requestSha256: String)

@Entity(tableName = "local_draft_receipt", foreignKeys = [ForeignKey(entity = LocalDraftRequest::class,
    parentColumns = ["originalKey"], childColumns = ["originalKey"])])
data class LocalDraftReceipt(@PrimaryKey val originalKey: String, val state: String,
    val responseBytes: ByteArray? = null, val signingKeyJson: String? = null)

@Dao
interface LocalDraftTransferDao {
    @Insert fun insert(row: LocalDraftTransfer)
    @Insert fun insertRequest(row: LocalDraftRequest)
    @Query("SELECT * FROM local_draft_transfer WHERE clientOrderId=:id") fun get(id: String): LocalDraftTransfer?
    @Query("SELECT COUNT(*) FROM local_draft_request WHERE clientOrderId=:id") fun count(id: String): Long
    @Query("SELECT * FROM local_draft_request WHERE clientOrderId=:id AND position=:position")
    fun request(id: String, position: Long): LocalDraftRequest?
    @Query("SELECT * FROM local_draft_request WHERE clientOrderId=:id AND position>:after ORDER BY position LIMIT 128")
    fun page(id: String, after: Long): List<LocalDraftRequest>
    @Insert fun insertReceipt(row: LocalDraftReceipt)
    @Query("SELECT * FROM local_draft_receipt WHERE originalKey=:key") fun receipt(key: String): LocalDraftReceipt?
    @Query("""UPDATE local_draft_receipt SET state='RECEIVED',responseBytes=:bytes
        WHERE originalKey=:key AND state='RESULT_UNKNOWN' AND responseBytes IS NULL AND signingKeyJson IS NULL""")
    fun received(key: String, bytes: ByteArray): Int
    @Query("""UPDATE local_draft_receipt SET state='VERIFIED',signingKeyJson=:keyJson
        WHERE originalKey=:key AND state='RECEIVED' AND responseBytes IS NOT NULL AND signingKeyJson IS NULL""")
    fun verified(key: String, keyJson: String): Int
}
