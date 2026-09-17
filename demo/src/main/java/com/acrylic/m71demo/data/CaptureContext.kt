package com.acrylic.m71demo.data

data class CaptureContext(
    val orderId: String,
    val originDeviceId: String,
    val orderScanSeq: Long,
    val deviceCaptureSeq: Long,
    val rawInput: String,
    val barcode: String,
    val inputSource: String,
    val receivedAt: Long,
    val receivedElapsedNs: Long,
    val bootSessionId: String,
    val snapshotId: String,
    val expectedDispatchCycle: Long?,
    val sourceRecoveryEpoch: String,
    val authorizationId: String,
    val orderRevision: Int = 1,
    val assignmentEpoch: Int = 1,
    val inputRevision: Int = 1,
    val operatorId: String = "DEMO_OPERATOR",
    @androidx.room.ColumnInfo(defaultValue = "'MANUAL'")
    val confirmationPolicy: String = "MANUAL",
)
