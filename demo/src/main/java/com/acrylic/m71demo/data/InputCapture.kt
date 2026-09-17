package com.acrylic.m71demo.data

import androidx.room.*

@Entity(tableName = "local_input_capture", indices = [
    Index(value = ["originDeviceId", "deviceCaptureSeq"], unique = true),
    Index(value = ["orderId", "orderScanSeq"], unique = true),
    Index(value = ["orderId", "state", "orderScanSeq"]),
], foreignKeys = [ForeignKey(entity = DemoOrder::class, parentColumns = ["id"],
    childColumns = ["orderId"], onDelete = ForeignKey.RESTRICT)])
data class InputCapture(
    @PrimaryKey val id: String,
    @Embedded val context: CaptureContext,
    val state: String = "RECEIVED",
    val error: String = "",
    val confirmedAtNs: Long? = null,
    val failureAcknowledged: Boolean = false,
    val inputCommittedNs: Long = 0,
    val confirmCommittedNs: Long = 0,
)
