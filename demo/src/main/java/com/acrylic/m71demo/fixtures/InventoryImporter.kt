package com.acrylic.m71demo.fixtures

import android.content.Context
import com.acrylic.m71demo.data.*
import com.acrylic.m71demo.domain.SkuParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class InventoryImporter @Inject constructor(@ApplicationContext private val context: Context,
    private val db: DemoDatabase) {
    fun ensureSample() {
        if (db.inventory().snapshot(SAMPLE_ID) != null) return
        val bytes = context.assets.open("sku.txt").use { it.readBytes() }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        check(hash == SAMPLE_SHA) { "内置sku摘要不匹配" }
        db.runInTransaction { import(bytes, hash) }
    }

    private fun import(bytes: ByteArray, hash: String) {
        db.inventory().insertSnapshot(InventorySnapshot(SAMPLE_ID, hash, 12639, "READONLY_SKU_SAMPLE"))
        var count = 0
        bytes.inputStream().bufferedReader(Charsets.ISO_8859_1).useLines { lines ->
            lines.filter { it.isNotBlank() }.chunked(200).forEach { chunk ->
                val bales = chunk.map { SkuParser.parse(it.toByteArray(Charsets.ISO_8859_1), SAMPLE_ID) }
                db.inventory().insertBales(bales)
                db.inventory().insertStates(bales.map { CloudBaleState(it.barcode) })
                count += bales.size
            }
        }
        check(count == 12639) { "sku行数不符" }
    }

    companion object {
        const val SAMPLE_ID = "sku-sample-20260915"
        const val SAMPLE_SHA = "3b6b8902184718c6714fd1ea00f87cd30c8d8527e744dea93c7e16e319495833"
    }
}
