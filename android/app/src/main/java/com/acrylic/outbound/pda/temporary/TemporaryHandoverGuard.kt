package com.acrylic.outbound.pda.temporary

import androidx.room.*

/** Retained originals reserve barcodes without creating editable copies of source captures or scans. */
@Entity(tableName = "temporary_handover_guard", indices = [Index("caseId")], foreignKeys = [
    ForeignKey(entity = TemporaryHandoverActivation::class, parentColumns = ["caseId"], childColumns = ["caseId"])])
data class TemporaryHandoverGuard(@PrimaryKey val barcode: String, val caseId: String, val captureId: String,
    val dispatchCycle: Long, val recordSha256: String)

@Dao
interface TemporaryHandoverGuardDao {
    @Insert fun insert(row: TemporaryHandoverGuard)
    @Query("SELECT * FROM temporary_handover_guard WHERE barcode=:barcode") fun get(barcode: String): TemporaryHandoverGuard?
    @Query("SELECT * FROM temporary_handover_guard WHERE caseId=:id ORDER BY barcode") fun forCase(id: String): List<TemporaryHandoverGuard>
}
