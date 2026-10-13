package com.acrylic.outbound.pda.independent

/** Mirrors the server's byte contract; parsing alone never activates a catalog or creates a task. */
object IndependentSkuParser {
    const val VERSION = "sku-gb18030-fixed57-v1"
    const val MAX_BYTES = 2 * 1024 * 1024
    fun parse(file: ByteArray): IndependentSkuPreview {
        if (file.isEmpty()) throw IndependentSkuException("EMPTY_FILE", "文件为空")
        if (file.size > MAX_BYTES) throw IndependentSkuException("FILE_TOO_LARGE", "文件不得超过2MiB")
        val bom = file.size >= 3 && file[0] == 0xef.toByte() && file[1] == 0xbb.toByte() && file[2] == 0xbf.toByte()
        val rows = mutableListOf<IndependentSkuRow>()
        var start = if (bom) 3 else 0
        var line = 0
        var ignored = 0
        for (i in start..file.size) {
            if (i < file.size && file[i] != '\n'.code.toByte()) continue
            if (i == file.size && start == i) break
            val bytes = file.copyOfRange(start, if (i > start && file[i - 1] == '\r'.code.toByte()) i - 1 else i)
            line++
            if (line > 40000) throw IndependentSkuException("ROW_LIMIT_EXCEEDED", "文件超过40000行，请核对文件格式")
            if (bytes.isEmpty()) ignored++ else rows.add(IndependentSkuFields.parse(bytes, line))
            start = i + 1
        }
        if (rows.isEmpty()) throw IndependentSkuException("EMPTY_FILE", "文件没有有效数据行")
        return summarize(IndependentSkuConflicts.apply(rows), ignored, bom)
    }
    private fun summarize(rows: List<IndependentSkuRow>, ignored: Int, bom: Boolean): IndependentSkuPreview {
        val valid = rows.filter { it.errorCode == null }
        val errors = rows.count { it.blocking }
        val duplicates = rows.size - valid.size - errors
        val status = if (errors > 0) "BLOCKED" else if (duplicates > 0) "NEEDS_DEDUP_CONFIRMATION" else "READY_FOR_REVIEW"
        var grams = 0L
        valid.forEach { grams = Math.addExact(grams, checkNotNull(it.parsedFields).weightGrams.toLong()) }
        val products = valid.map { checkNotNull(it.parsedFields).deliveryCode }.distinct().size
        return IndependentSkuPreview(VERSION, status, rows.size, valid.size, errors, duplicates, ignored, products, grams.toString(), bom, rows)
    }
}
