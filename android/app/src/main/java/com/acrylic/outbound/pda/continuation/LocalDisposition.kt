package com.acrylic.outbound.pda.continuation

import androidx.room.*
import com.acrylic.outbound.pda.work.WorkScan
import org.json.*

@Entity(tableName = "local_disposition", foreignKeys = [ForeignKey(entity = WorkScan::class,
    parentColumns = ["captureId"], childColumns = ["captureId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["assignmentId", "ordinal"], unique = true), Index(value = ["evidenceId"], unique = true)])
data class LocalDisposition(@PrimaryKey val captureId: String, val assignmentId: String, val evidenceId: String,
    val action: String, val proofJson: String, val ordinal: Int)

@Dao
abstract class DispositionDao {
    @Query("SELECT * FROM local_disposition WHERE assignmentId=:id ORDER BY ordinal") abstract fun all(id: String): List<LocalDisposition>
    @Insert abstract fun insert(row: LocalDisposition)
    fun proofs(id: String): JSONArray = JSONArray(all(id).map { JSONObject(it.proofJson) })
}
