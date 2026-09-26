package com.acrylic.outbound.pda.evidence

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "local_e2a_transfer", foreignKeys = [ForeignKey(entity = LocalE2aStop::class,
    parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"])])
data class LocalE2aTransfer(@PrimaryKey val clientOrderId: String, val manifestJson: String,
    val manifestBytesHash: String, val setHash: String, val stopOriginalKey: String,
    val verifyOriginalKey: String, val pageCount: Int, val rawCount: Long, val requestSetSha256: String,
    val devicePublicKey: String)

/** RAW stores immutable object metadata; the existing snapshot owns the exact body bytes. */
@Entity(tableName = "local_e2a_request", primaryKeys = ["clientOrderId", "operation", "ordinal"],
    indices = [Index(value = ["originalKey"], unique = true)],
    foreignKeys = [ForeignKey(entity = LocalE2aStop::class, parentColumns = ["clientOrderId"],
        childColumns = ["clientOrderId"])])
data class LocalE2aRequest(val clientOrderId: String, val operation: String, val ordinal: Long,
    val originalKey: String, val requestJson: String, val requestSha256: String)

@Dao
interface LocalE2aTransferDao {
    @Insert fun insert(row: LocalE2aTransfer)
    @Insert fun insertRequest(row: LocalE2aRequest)
    @Query("SELECT * FROM local_e2a_transfer WHERE clientOrderId=:id")
    fun get(id: String): LocalE2aTransfer?
    @Query("SELECT * FROM local_e2a_request WHERE clientOrderId=:id AND operation=:operation AND ordinal=:ordinal")
    fun request(id: String, operation: String, ordinal: Long): LocalE2aRequest?
    @Query("SELECT COUNT(*) FROM local_e2a_request WHERE clientOrderId=:id")
    fun requestCount(id: String): Long
}
