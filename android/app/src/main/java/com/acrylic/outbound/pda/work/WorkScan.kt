package com.acrylic.outbound.pda.work

import androidx.room.*

@Entity(tableName = "work_scan", indices = [Index(value = ["assignmentId", "orderScanSeq"], unique = true),
    Index(value = ["assignmentId", "status", "orderScanSeq"])],
    foreignKeys = [ForeignKey(entity = WorkCapture::class, parentColumns = ["id"], childColumns = ["captureId"]),
        ForeignKey(entity = WorkOrder::class, parentColumns = ["assignmentId"], childColumns = ["assignmentId"])])
data class WorkScan(@PrimaryKey val captureId: String, val assignmentId: String, val orderScanSeq: Long,
    val barcode: String, val baleId: String, val deliveryCode: String, val model: String, val name: String,
    val rawWeight: String, val weightGrams: Long, val expectedDispatchCycle: Long,
    val savedAt: String, val localSeq: Long, val status: String = "ACTIVE")

@Entity(tableName = "work_guard", indices = [Index(value = ["captureId"], unique = true), Index("assignmentId")],
    foreignKeys = [ForeignKey(entity = WorkScan::class, parentColumns = ["captureId"], childColumns = ["captureId"])])
data class WorkGuard(@PrimaryKey val barcode: String, val captureId: String, val assignmentId: String, val dispatchCycle: Long)

@Entity(tableName = "work_event", indices = [Index(value = ["originDeviceId", "localSeq"], unique = true),
    Index(value = ["assignmentId", "localSeq"]), Index("captureId")],
    foreignKeys = [ForeignKey(entity = WorkOrder::class, parentColumns = ["assignmentId"], childColumns = ["assignmentId"]),
        ForeignKey(entity = WorkCapture::class, parentColumns = ["id"], childColumns = ["captureId"])])
data class WorkEvent(@PrimaryKey val id: String, val assignmentId: String, val captureId: String?,
    val originDeviceId: String, val operatorId: String, val localSeq: Long, val type: String,
    val detail: String, val wallMs: Long, val elapsedMs: Long, val boot: Int)
