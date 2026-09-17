package com.acrylic.outbound.pda.submission

import androidx.room.*
import com.acrylic.outbound.pda.work.WorkOrder

@Entity(tableName = "local_submission", indices = [Index(value = ["assignmentId"], unique = true)],
    foreignKeys = [ForeignKey(entity = WorkOrder::class, parentColumns = ["assignmentId"], childColumns = ["assignmentId"])])
data class LocalSubmission(@PrimaryKey val id: String, val assignmentId: String, val payload: String, val sha256: String,
    val state: String = "FROZEN", val receipt: String? = null, val error: String = "", val autoRetry: Boolean = false)
