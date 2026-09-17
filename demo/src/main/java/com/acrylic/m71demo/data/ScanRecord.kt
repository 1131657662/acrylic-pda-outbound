package com.acrylic.m71demo.data

import androidx.room.*

@Entity(tableName = "local_scan", indices = [
    Index(value = ["originDeviceId", "deviceCaptureSeq"], unique = true),
    Index(value = ["originDeviceId", "localSeq"], unique = true),
    Index(value = ["orderId", "orderScanSeq"], unique = true),
    Index(value = ["orderId", "status", "orderScanSeq"]),
], foreignKeys = [ForeignKey(entity = InputCapture::class, parentColumns = ["id"],
    childColumns = ["id"], onDelete = ForeignKey.RESTRICT)])
data class ScanRecord(
    @PrimaryKey val id: String,
    @Embedded val context: CaptureContext,
    val weightGrams: Long,
    val rawWeight: String,
    val deliveryCode: String,
    val localSeq: Long,
    val savedAt: Long,
    val confirmedAtNs: Long,
    val status: String = "ACTIVE",
)

@Entity(tableName = "local_barcode_guard", indices = [Index("orderId"), Index("scanId")],
    foreignKeys = [ForeignKey(entity = ScanRecord::class, parentColumns = ["id"],
        childColumns = ["scanId"], onDelete = ForeignKey.RESTRICT)])
data class BarcodeGuard(
    @PrimaryKey val barcode: String,
    val orderId: String,
    val scanId: String,
    val dispatchCycle: Long,
    val state: String = "SCANNED",
)
