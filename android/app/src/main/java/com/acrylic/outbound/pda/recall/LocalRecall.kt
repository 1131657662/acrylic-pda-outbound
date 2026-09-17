package com.acrylic.outbound.pda.recall

import androidx.room.*

@Entity(tableName = "local_recall", indices = [Index(value = ["assignmentId", "currentMarker"], unique = true)])
data class LocalRecall(@PrimaryKey val id: String, val assignmentId: String, val requestJson: String,
    val packageId: String? = null, val payload: String? = null, val sha256: String? = null,
    val state: String = "RECALL_PENDING", val proof: String? = null, @ColumnInfo(defaultValue = "1") val currentMarker: Int? = 1)
