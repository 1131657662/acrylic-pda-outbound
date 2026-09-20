package com.acrylic.outbound.pda.temporary

import androidx.room.*

@Entity(tableName = "temporary_request", indices = [Index(value = ["userId", "deviceId", "warehouseId"], unique = true)])
data class TemporaryRequest(@PrimaryKey val key: String, val userId: String, val deviceId: String, val warehouseId: String,
    val recoveryEpoch: String, val userAuthVersion: String, val deviceVersion: String, val bodyJson: String,
    val signingKeyJson: String, val receiptJson: String? = null)

@Entity(tableName = "temporary_preparation", indices = [Index(value = ["requestKey"], unique = true)],
    foreignKeys = [ForeignKey(entity = TemporaryRequest::class, parentColumns = ["key"], childColumns = ["requestKey"])])
data class TemporaryPreparation(@PrimaryKey val authorizationId: String, val requestKey: String, val bundleId: String,
    val manifestSha256: String, val activatedAt: String, val inventoryRows: Int, val availabilityRows: Int,
    val ackKey: String, val ackBodyJson: String, val ackJson: String? = null)

@Entity(tableName = "temporary_part_chunk", primaryKeys = ["authorizationId", "part", "ordinal"],
    foreignKeys = [ForeignKey(entity = TemporaryPreparation::class, parentColumns = ["authorizationId"], childColumns = ["authorizationId"])])
data class TemporaryPartChunk(val authorizationId: String, val part: String, val ordinal: Int, val bytes: ByteArray)

@Dao
interface TemporaryDao {
    @Query("SELECT * FROM temporary_request WHERE userId=:user AND deviceId=:device AND warehouseId=:warehouse")
    fun current(user: String, device: String, warehouse: String): TemporaryRequest?
    @Query("SELECT * FROM temporary_request WHERE `key`=:key") fun request(key: String): TemporaryRequest?
    @Query("SELECT EXISTS(SELECT 1 FROM temporary_request WHERE recoveryEpoch!=:epoch)") fun hasOtherEpoch(epoch: String): Boolean
    @Insert fun insertRequest(row: TemporaryRequest)
    @Query("UPDATE temporary_request SET receiptJson=:receipt WHERE `key`=:key AND receiptJson IS NULL")
    fun received(key: String, receipt: String): Int
    @Query("SELECT * FROM temporary_preparation WHERE requestKey=:key") fun prepared(key: String): TemporaryPreparation?
    @Insert fun insertPreparation(row: TemporaryPreparation)
    @Insert fun insertChunk(row: TemporaryPartChunk)
    @Query("SELECT * FROM temporary_part_chunk WHERE authorizationId=:id AND part=:part AND ordinal=:ordinal")
    fun chunk(id: String, part: String, ordinal: Int): TemporaryPartChunk?
    @Query("SELECT COUNT(*) FROM temporary_part_chunk WHERE authorizationId=:id AND part=:part") fun count(id: String, part: String): Int
    @Query("UPDATE temporary_preparation SET ackJson=:ack WHERE authorizationId=:id AND ackJson IS NULL")
    fun acknowledged(id: String, ack: String): Int
}
