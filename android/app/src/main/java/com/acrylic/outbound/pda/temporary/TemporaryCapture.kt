package com.acrylic.outbound.pda.temporary

import androidx.room.*

@Entity(tableName = "temporary_capture", indices = [Index(value = ["clientOrderId", "orderScanSeq"], unique = true),
    Index(value = ["originDeviceId", "deviceCaptureSeq"], unique = true), Index(value = ["clientOrderId", "state", "orderScanSeq"])],
    foreignKeys = [ForeignKey(entity = TemporaryDraft::class, parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"])])
data class TemporaryCapture(@PrimaryKey val id: String, val clientOrderId: String, val originDeviceId: String,
    val operatorId: String, val orderScanSeq: Long, val deviceCaptureSeq: Long, val rawInput: String, val barcode: String,
    val inputSource: String, val receivedWallMs: Long, val receivedElapsedMs: Long, val receivedBoot: Int,
    val authorizationId: String, val authorizationEpoch: String, val snapshotId: String, val sourceRecoveryEpoch: String,
    val orderRevision: Long, val expectedDispatchCycle: Long?, val state: String = "RECEIVED", val error: String = "",
    val confirmedAt: String? = null, val failureAckAt: String? = null)

@Entity(tableName = "temporary_scan", indices = [Index(value = ["clientOrderId", "orderScanSeq"], unique = true)],
    foreignKeys = [ForeignKey(entity = TemporaryCapture::class, parentColumns = ["id"], childColumns = ["captureId"]),
        ForeignKey(entity = TemporaryDraft::class, parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"])])
data class TemporaryScan(@PrimaryKey val captureId: String, val clientOrderId: String, val orderScanSeq: Long,
    val barcode: String, val baleId: String, val deliveryCode: String, val model: String, val name: String,
    val rawWeight: String, val weightGrams: Long, val expectedDispatchCycle: Long, val savedAt: String,
    val localSeq: Long, val status: String = "ACTIVE")

@Entity(tableName = "temporary_guard", indices = [Index(value = ["captureId"], unique = true)],
    foreignKeys = [ForeignKey(entity = TemporaryScan::class, parentColumns = ["captureId"], childColumns = ["captureId"])])
data class TemporaryGuard(@PrimaryKey val barcode: String, val captureId: String)

@Entity(tableName = "temporary_event", indices = [Index(value = ["originDeviceId", "localSeq"], unique = true),
    Index(value = ["clientOrderId", "localSeq"]), Index("captureId")],
    foreignKeys = [ForeignKey(entity = TemporaryDraft::class, parentColumns = ["clientOrderId"], childColumns = ["clientOrderId"]),
        ForeignKey(entity = TemporaryCapture::class, parentColumns = ["id"], childColumns = ["captureId"])])
data class TemporaryEvent(@PrimaryKey val id: String, val clientOrderId: String, val captureId: String?,
    val originDeviceId: String, val operatorId: String, val localSeq: Long, val type: String, val detail: String,
    val wallMs: Long, val elapsedMs: Long, val boot: Int)
