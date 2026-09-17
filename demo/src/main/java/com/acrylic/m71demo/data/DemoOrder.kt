package com.acrylic.m71demo.data

import androidx.room.*

@Entity(tableName = "local_order")
data class DemoOrder(
    @PrimaryKey val id: String,
    val vehicle: String,
    val snapshotId: String,
    val authorizationId: String,
    val source: String = "TEMP_TEST",
    val state: String = "SCANNING",
    val nextOrderScanSeq: Long = 1,
    val actualCount: Int = 0,
    val weightGrams: Long = 0,
    val createdAt: Long,
    val recoveryEpoch: String = "DEMO_EPOCH_1",
    val workRound: Int = 1,
)

@Entity(tableName = "local_order_line", primaryKeys = ["orderId", "deliveryCode"],
    foreignKeys = [ForeignKey(entity = DemoOrder::class, parentColumns = ["id"],
        childColumns = ["orderId"], onDelete = ForeignKey.RESTRICT)])
data class OrderLine(
    val orderId: String,
    val deliveryCode: String,
    val plannedCount: Int,
    val actualCount: Int = 0,
    val weightGrams: Long = 0,
)

@Entity(tableName = "local_submission", indices = [Index(value = ["orderId"], unique = true)])
data class FrozenSubmission(
    @PrimaryKey val id: String,
    val orderId: String,
    val payloadPath: String,
    val payloadSha256: String,
    val createdAt: Long,
    val state: String = "FROZEN_TEST_ONLY",
)
