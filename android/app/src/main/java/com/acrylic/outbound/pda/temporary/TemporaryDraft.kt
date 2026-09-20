package com.acrylic.outbound.pda.temporary

import androidx.room.*

@Entity(tableName = "temporary_draft", indices = [Index("authorizationId"),
    Index(value = ["creatorDeviceId", "operatorId", "warehouseId", "clientOrderId"])],
    foreignKeys = [ForeignKey(entity = TemporaryPreparation::class, parentColumns = ["authorizationId"], childColumns = ["authorizationId"])])
data class TemporaryDraft(@PrimaryKey val clientOrderId: String, val creatorDeviceId: String, val operatorId: String,
    val warehouseId: String, val authorizationId: String, val temporaryNo: String, val createdAt: String,
    val vehicleNo: String = "", val containerNo: String = "", val reason: String = "", val revision: Long = 0,
    val state: String = "DRAFT", val nextScanSeq: Long = 1, val planSha256: String = "")

@Entity(tableName = "temporary_line", primaryKeys = ["clientOrderId", "deliveryCode"],
    foreignKeys = [ForeignKey(entity = TemporaryDraft::class, parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"])])
data class TemporaryLine(val clientOrderId: String, val deliveryCode: String, val productId: String,
    val model: String, val name: String, val plannedCount: Long)

@Dao
interface TemporaryDraftDao {
    @Insert fun insert(row: TemporaryDraft)
    @Update fun update(row: TemporaryDraft)
    @Query("SELECT * FROM temporary_draft WHERE clientOrderId=:id") fun get(id: String): TemporaryDraft?
    @Query("""SELECT * FROM temporary_draft WHERE creatorDeviceId=:device AND operatorId=:user AND warehouseId=:warehouse
        AND clientOrderId>:after ORDER BY clientOrderId LIMIT 21""")
    fun page(device: String, user: String, warehouse: String, after: String): List<TemporaryDraft>
    @Insert fun insertLines(rows: List<TemporaryLine>)
    @Query("DELETE FROM temporary_line WHERE clientOrderId=:id") fun deleteLines(id: String)
    @Query("SELECT * FROM temporary_line WHERE clientOrderId=:id ORDER BY deliveryCode") fun lines(id: String): List<TemporaryLine>
    @Query("SELECT * FROM temporary_line WHERE clientOrderId=:id AND deliveryCode=:code") fun line(id: String, code: String): TemporaryLine?
}
