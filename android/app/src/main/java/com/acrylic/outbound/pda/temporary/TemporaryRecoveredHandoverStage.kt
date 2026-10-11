package com.acrylic.outbound.pda.temporary

import androidx.room.*

/** A retained recovery download has no ordinary TEMP preparation or scanning authorization. */
@Entity(tableName = "temporary_recovered_handover_stage")
data class TemporaryRecoveredHandoverStage(@PrimaryKey val caseId: String, val qualificationId: String,
    val qualificationSha256: String, val recipientSessionSha256: String, val sourceRecoveryEpoch: String,
    val targetRecoveryEpoch: String, val userId: String, val deviceId: String, val warehouseId: String,
    val userAuthVersion: String, val deviceVersion: String, val sessionId: String, val localSessionId: String,
    val manifestSha256: String, val manifestLength: Int, val payloadSha256: String, val payloadLength: Int,
    val signingKeyJson: String, val state: String = "STAGED_RECOVERY_READ")

@Dao
interface TemporaryRecoveredHandoverStageDao {
    @Insert fun insert(row: TemporaryRecoveredHandoverStage)
    @Query("SELECT * FROM temporary_recovered_handover_stage WHERE caseId=:id")
    fun get(id: String): TemporaryRecoveredHandoverStage?
    @Query("SELECT caseId FROM temporary_recovered_handover_stage WHERE userId=:user AND deviceId=:device AND warehouseId=:warehouse ORDER BY caseId DESC LIMIT :limit")
    fun cases(user: String, device: String, warehouse: String, limit: Int): List<String>
}
