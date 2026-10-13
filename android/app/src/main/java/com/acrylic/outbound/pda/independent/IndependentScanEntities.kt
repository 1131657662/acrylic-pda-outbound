package com.acrylic.outbound.pda.independent

import androidx.room.*
import com.acrylic.outbound.pda.security.TimeSample
import java.util.UUID

@Entity(tableName = "independent_capture", indices = [Index(value = ["taskId", "sequence"], unique = true),
    Index(value = ["deviceId", "deviceSequence"], unique = true)], foreignKeys = [
    ForeignKey(entity = IndependentTask::class, parentColumns = ["id"], childColumns = ["taskId"])])
data class IndependentCapture(@PrimaryKey val id: String, val taskId: String, val sequence: Long,
    val deviceId: String, val deviceSequence: Long, val rawInput: String, val barcode: String, val inputSource: String,
    val wall: Long, val elapsed: Long, val boot: Int, val capturedTrustedAt: String)

@Entity(tableName = "independent_capture_state", foreignKeys = [
    ForeignKey(entity = IndependentCapture::class, parentColumns = ["id"], childColumns = ["captureId"])])
data class IndependentCaptureState(@PrimaryKey val captureId: String, val state: String = "RECEIVED",
    val error: String = "", val confirmedAt: String? = null, val failureAckAt: String? = null)

data class IndependentCaptureView(@Embedded val capture: IndependentCapture, @Embedded val processing: IndependentCaptureState)

@Entity(tableName = "independent_scan", indices = [Index("taskId"), Index("barcode")], foreignKeys = [
    ForeignKey(entity = IndependentCapture::class, parentColumns = ["id"], childColumns = ["captureId"])])
data class IndependentScan(@PrimaryKey val captureId: String, val taskId: String, val sequence: Long,
    val barcode: String, val deliveryCode: String, val model: String, val name: String, val rawWeight: String,
    val weightGrams: Long, val skuLineNo: Int, val skuRawText: String, val savedTrustedAt: String)

@Entity(tableName = "independent_guard", indices = [Index(value = ["captureId"], unique = true), Index("taskId")], foreignKeys = [
    ForeignKey(entity = IndependentScan::class, parentColumns = ["captureId"], childColumns = ["captureId"])])
data class IndependentGuard(@PrimaryKey val barcode: String, val captureId: String, val taskId: String)

@Entity(tableName = "independent_void", indices = [Index("taskId")], foreignKeys = [
    ForeignKey(entity = IndependentScan::class, parentColumns = ["captureId"], childColumns = ["captureId"])])
data class IndependentVoid(@PrimaryKey val captureId: String, val taskId: String, val reason: String,
    val wall: Long, val elapsed: Long, val boot: Int, val authorizedAt: String)

@Entity(tableName = "independent_device_state")
data class IndependentDeviceState(@PrimaryKey val deviceId: String, val nextCaptureSeq: Long = 1)

data class IndependentScanView(@Embedded val scan: IndependentScan, val status: String)

internal data class IndependentInput(val taskId: String, val raw: String, val source: String, val time: TimeSample,
    val id: String = UUID.randomUUID().toString())

internal data class IndependentScanEnvironment(val sample: () -> TimeSample, val actor: () -> IndependentTaskActor,
    val feedback: (String) -> Unit = {})
