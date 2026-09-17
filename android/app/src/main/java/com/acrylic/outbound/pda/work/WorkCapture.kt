package com.acrylic.outbound.pda.work

import androidx.room.*

data class CaptureContext(val assignmentId: String, val orderId: String, val originDeviceId: String,
    val operatorId: String, val warehouseId: String, val orderScanSeq: Long, val deviceCaptureSeq: Long,
    val rawInput: String, val barcode: String, val inputSource: String, val confirmationPolicy: String,
    val receivedWallMs: Long, val receivedElapsedMs: Long, val receivedBoot: Int,
    val snapshotId: String, val expectedDispatchCycle: Long?, val sourceRecoveryEpoch: String,
    val authorizationId: String, val assignmentEpoch: String, val orderRevision: Int, val inputRevision: Int = 1, @ColumnInfo(defaultValue = "''") val originAssignmentId: String = "")

@Entity(tableName = "work_capture", indices = [
    Index(value = ["originDeviceId", "deviceCaptureSeq"], unique = true),
    Index(value = ["orderId", "orderScanSeq"], unique = true),
    Index(value = ["assignmentId", "orderScanSeq"]),
    Index(value = ["assignmentId", "state", "orderScanSeq"])],
    foreignKeys = [ForeignKey(entity = WorkOrder::class, parentColumns = ["assignmentId"], childColumns = ["assignmentId"])])
data class WorkCapture(@PrimaryKey val id: String, @Embedded val context: CaptureContext,
    val state: String = "RECEIVED", val error: String = "", val confirmedAt: String? = null,
    val failureAckAt: String? = null)
