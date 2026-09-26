package com.acrylic.outbound.pda.evidence

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "local_e2a_object", primaryKeys = ["clientOrderId", "kind", "recordId"],
    indices = [Index(value = ["objectId"], unique = true), Index(value = ["clientOrderId", "kind", "deviceSeq"])],
    foreignKeys = [ForeignKey(entity = LocalE2aStop::class, parentColumns = ["clientOrderId"],
        childColumns = ["clientOrderId"])])
data class LocalE2aObject(val clientOrderId: String, val kind: String, val recordId: String,
    val objectId: String, val deviceSeq: Long, val recordStatus: String,
    val bytesSha256: String, val rawBytes: ByteArray, val signingKeyJson: String? = null)
