package com.acrylic.outbound.pda.work

import androidx.room.*
import com.acrylic.outbound.pda.data.PreparedOrder

@Entity(tableName = "work_order", indices = [Index(value = ["orderId"], unique = true)],
    foreignKeys = [ForeignKey(entity = PreparedOrder::class, parentColumns = ["assignmentId"], childColumns = ["assignmentId"])])
data class WorkOrder(@PrimaryKey val assignmentId: String, val orderId: String, val vehicleNo: String,
    val plannedTotal: Long, val nextOrderScanSeq: Long, val actualCount: Long = 0, val weightGrams: Long = 0,
    val startedAt: String? = null, val state: String = "SCANNING")

@Entity(tableName = "work_line", primaryKeys = ["assignmentId", "deliveryCode"],
    foreignKeys = [ForeignKey(entity = WorkOrder::class, parentColumns = ["assignmentId"], childColumns = ["assignmentId"])])
data class WorkLine(val assignmentId: String, val deliveryCode: String, val model: String, val name: String,
    val plannedCount: Long, val actualCount: Long = 0, val weightGrams: Long = 0)

@Entity(tableName = "work_device")
data class WorkDevice(@PrimaryKey val deviceId: String, val nextCaptureSeq: Long = 1, val nextEventSeq: Long = 1)
