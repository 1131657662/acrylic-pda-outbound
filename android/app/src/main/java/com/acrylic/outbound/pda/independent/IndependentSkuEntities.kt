package com.acrylic.outbound.pda.independent

import androidx.room.*

@Entity(tableName = "independent_sku_original")
data class IndependentSkuOriginal(@PrimaryKey val sha256: String, val byteLength: Int, val parserVersion: String)

@Entity(tableName = "independent_sku_chunk", primaryKeys = ["sha256", "part"], foreignKeys = [
    ForeignKey(entity = IndependentSkuOriginal::class, parentColumns = ["sha256"], childColumns = ["sha256"])])
data class IndependentSkuChunk(val sha256: String, val part: Int, val bytes: ByteArray)

@Entity(tableName = "independent_sku_import", indices = [Index("sha256")], foreignKeys = [
    ForeignKey(entity = IndependentSkuOriginal::class, parentColumns = ["sha256"], childColumns = ["sha256"])])
data class IndependentSkuImport(@PrimaryKey val id: String, val sha256: String, val displayName: String,
    val selectedWall: Long, val selectedElapsed: Long, val selectedBoot: Int)

@Entity(tableName = "independent_sku_decision", foreignKeys = [
    ForeignKey(entity = IndependentSkuImport::class, parentColumns = ["id"], childColumns = ["importId"])])
data class IndependentSkuDecision(@PrimaryKey val importId: String, val outcome: String,
    val previousImportId: String?, val duplicatesConfirmed: Boolean,
    val wall: Long, val elapsed: Long, val boot: Int)

@Entity(tableName = "independent_sku_active", indices = [Index("importId")], foreignKeys = [
    ForeignKey(entity = IndependentSkuImport::class, parentColumns = ["id"], childColumns = ["importId"])])
data class IndependentSkuActive(@PrimaryKey val slot: Int = 1, val importId: String)

data class IndependentSkuHistory(val sequence: Long, @Embedded val imported: IndependentSkuImport)
