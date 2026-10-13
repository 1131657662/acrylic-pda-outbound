package com.acrylic.outbound.pda.independent

import androidx.room.*

@Dao
interface IndependentTaskDao {
    @Insert fun insertTask(row: IndependentTask)
    @Insert fun insertLines(rows: List<IndependentTaskLine>)
    @Insert fun insertState(row: IndependentTaskState)
    @Insert fun insertEvent(row: IndependentTaskEvent)
    @Insert fun insertRequest(row: IndependentTaskRequest)
    @Query("SELECT * FROM independent_task_request WHERE id=:id") fun request(id: String): IndependentTaskRequest?
    @Query("SELECT * FROM independent_task_request WHERE operatorId=:operator AND deviceId=:device AND warehouseId=:warehouse ORDER BY rowid DESC LIMIT 1")
    fun latestRequest(operator: String, device: String, warehouse: String): IndependentTaskRequest?
    @Query("SELECT rowid AS sequence,* FROM independent_task_request WHERE operatorId=:operator AND deviceId=:device AND warehouseId=:warehouse AND rowid<:before ORDER BY rowid DESC LIMIT 25")
    fun requestPage(operator: String, device: String, warehouse: String, before: Long): List<IndependentTaskRequestHistory>
    @Update fun updateState(row: IndependentTaskState)
    @Query("SELECT * FROM independent_task WHERE id=:id") fun task(id: String): IndependentTask?
    @Query("SELECT * FROM independent_task_line WHERE taskId=:id ORDER BY deliveryCode") fun lines(id: String): List<IndependentTaskLine>
    @Query("SELECT * FROM independent_task_state WHERE taskId=:id") fun state(id: String): IndependentTaskState?
    @Query("SELECT * FROM independent_task_event WHERE taskId=:id ORDER BY sequence") fun events(id: String): List<IndependentTaskEvent>
    @Query("SELECT rowid AS sequence,* FROM independent_task WHERE operatorId=:operator AND deviceId=:device AND warehouseId=:warehouse AND rowid<:before ORDER BY rowid DESC LIMIT 25")
    fun page(operator: String, device: String, warehouse: String, before: Long): List<IndependentTaskHistory>
    @Query("SELECT COUNT(*) FROM independent_task") fun count(): Long
}
