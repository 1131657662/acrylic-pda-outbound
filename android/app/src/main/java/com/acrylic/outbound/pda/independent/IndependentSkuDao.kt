package com.acrylic.outbound.pda.independent

import androidx.room.*

@Dao
interface IndependentSkuDao {
    @Insert fun insertOriginal(row: IndependentSkuOriginal)
    @Insert fun insertChunks(rows: List<IndependentSkuChunk>)
    @Insert fun insertImport(row: IndependentSkuImport)
    @Insert fun insertDecision(row: IndependentSkuDecision)
    @Upsert fun setActive(row: IndependentSkuActive)
    @Query("SELECT * FROM independent_sku_original WHERE sha256=:sha") fun original(sha: String): IndependentSkuOriginal?
    @Query("SELECT * FROM independent_sku_chunk WHERE sha256=:sha ORDER BY part") fun chunks(sha: String): List<IndependentSkuChunk>
    @Query("SELECT * FROM independent_sku_import WHERE id=:id") fun imported(id: String): IndependentSkuImport?
    @Query("SELECT * FROM independent_sku_decision WHERE importId=:id") fun decision(id: String): IndependentSkuDecision?
    @Query("SELECT * FROM independent_sku_active WHERE slot=1") fun active(): IndependentSkuActive?
    @Query("SELECT * FROM independent_sku_import ORDER BY rowid DESC LIMIT 1") fun latest(): IndependentSkuImport?
    @Query("SELECT rowid AS sequence,* FROM independent_sku_import WHERE rowid<:before ORDER BY rowid DESC LIMIT 25")
    fun history(before: Long): List<IndependentSkuHistory>
}
