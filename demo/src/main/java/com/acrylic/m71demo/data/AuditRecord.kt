package com.acrylic.m71demo.data

import androidx.room.*

@Entity(tableName = "local_operation_event", indices = [
    Index(value = ["originDeviceId", "localSeq"], unique = true),
    Index(value = ["orderId", "localSeq"]),
    Index(value = ["orderId", "originDeviceId", "localSeq"]),
])
data class AuditRecord(
    @PrimaryKey val id: String,
    val originDeviceId: String,
    val localSeq: Long,
    val orderId: String,
    val captureId: String?,
    val type: String,
    val reason: String,
    val occurredAt: Long,
    val elapsedNs: Long,
    val bootSessionId: String,
)

@Entity(tableName = "local_device_state")
data class DeviceState(
    @PrimaryKey val id: String,
    val nextCaptureSeq: Long = 1,
    val nextAuditSeq: Long = 1,
    val nextFixtureSerial: Long = 1,
)

@Entity(tableName = "local_user_auth")
data class DemoAuthorization(
    @PrimaryKey val id: String,
    val deviceId: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val bootSessionId: String,
    val elapsedExpiresNs: Long,
    val signature: String,
)
