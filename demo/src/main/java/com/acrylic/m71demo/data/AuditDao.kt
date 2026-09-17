package com.acrylic.m71demo.data

import androidx.room.*

@Dao
interface AuditDao {
    @Insert fun insert(event: AuditRecord)
    @Insert fun insertBatch(events: List<AuditRecord>)
    @Insert fun insertDevice(device: DeviceState)
    @Update fun updateDevice(device: DeviceState)
    @Query("SELECT * FROM local_device_state LIMIT 1") fun device(): DeviceState?
    @Query("SELECT * FROM local_operation_event WHERE orderId=:id AND localSeq>:after ORDER BY localSeq LIMIT :limit")
    fun page(id: String, after: Long, limit: Int): List<AuditRecord>
    @Query("SELECT * FROM local_operation_event WHERE orderId=:id AND (originDeviceId>:origin OR (originDeviceId=:origin AND localSeq>:after)) ORDER BY originDeviceId,localSeq LIMIT :limit")
    fun exportPage(id: String, origin: String, after: Long, limit: Int): List<AuditRecord>
    @Insert fun insertAuthorization(auth: DemoAuthorization)
    @Query("SELECT * FROM local_user_auth WHERE id=:id") fun authorization(id: String): DemoAuthorization?
}
