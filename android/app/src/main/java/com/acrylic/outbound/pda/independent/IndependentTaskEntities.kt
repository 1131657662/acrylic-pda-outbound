package com.acrylic.outbound.pda.independent

import androidx.room.*

@Entity(tableName = "independent_task", indices = [Index("importId"), Index(value = ["operatorId", "deviceId", "warehouseId"])],
    foreignKeys = [ForeignKey(entity = IndependentSkuImport::class, parentColumns = ["id"], childColumns = ["importId"])])
data class IndependentTask(@PrimaryKey val id: String, val importId: String, val skuSha256: String,
    val credentialId: String, val signedCredential: String, val trustedKeyJson: String, val timeAnchorJson: String, val localSessionId: String,
    val operatorId: String, val deviceId: String, val warehouseId: String, val recoveryEpoch: String,
    val vehicleNo: String, val reason: String, val plannedTotal: Long, val commandSha256: String,
    val createdWall: Long, val createdElapsed: Long, val createdBoot: Int)

@Entity(tableName = "independent_task_line", primaryKeys = ["taskId", "deliveryCode"],
    foreignKeys = [ForeignKey(entity = IndependentTask::class, parentColumns = ["id"], childColumns = ["taskId"])])
data class IndependentTaskLine(val taskId: String, val deliveryCode: String, val model: String, val name: String, val plannedCount: Long)

@Entity(tableName = "independent_task_state", foreignKeys = [
    ForeignKey(entity = IndependentTask::class, parentColumns = ["id"], childColumns = ["taskId"])])
data class IndependentTaskState(@PrimaryKey val taskId: String, val state: String = "PLANNED",
    val nextScanSeq: Long = 1, val nextEventSeq: Long = 1, val actualCount: Long = 0, val weightGrams: Long = 0)

@Entity(tableName = "independent_task_event", indices = [Index(value = ["taskId", "sequence"], unique = true)],
    foreignKeys = [ForeignKey(entity = IndependentTask::class, parentColumns = ["id"], childColumns = ["taskId"])])
data class IndependentTaskEvent(@PrimaryKey val id: String, val taskId: String, val sequence: Long,
    val type: String, val detail: String, val wall: Long, val elapsed: Long, val boot: Int)

data class IndependentTaskView(val task: IndependentTask, val lines: List<IndependentTaskLine>,
    val state: IndependentTaskState, val events: List<IndependentTaskEvent>)

@Entity(tableName = "independent_task_request", indices = [Index("importId"), Index(value = ["operatorId", "deviceId", "warehouseId"])], foreignKeys = [
    ForeignKey(entity = IndependentSkuImport::class, parentColumns = ["id"], childColumns = ["importId"])])
data class IndependentTaskRequest(@PrimaryKey val id: String, val importId: String, val commandJson: String,
    val commandSha256: String, val authorizationJson: String, val operatorId: String, val deviceId: String, val warehouseId: String)

data class IndependentTaskHistory(val sequence: Long, @Embedded val task: IndependentTask)
data class IndependentTaskRequestHistory(val sequence: Long, @Embedded val request: IndependentTaskRequest)
data class IndependentTaskActor(val operatorId: String, val deviceId: String, val warehouseId: String) {
    fun requireOwner(row: IndependentTask) = check(row.operatorId == operatorId && row.deviceId == deviceId && row.warehouseId == warehouseId) { "只可读取原人原机同仓独立记录" }
    fun requireOwner(row: IndependentTaskRequest) = check(row.operatorId == operatorId && row.deviceId == deviceId && row.warehouseId == warehouseId) { "只可读取原人原机同仓请求" }
}
