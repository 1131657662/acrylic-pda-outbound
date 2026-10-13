package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.independent.*

internal object IndependentSkuDisplay {
    fun summary(review: IndependentSkuReview?): String {
        if (review == null) return "尚无原件"
        val p = review.preview
        val source = "文件：${review.imported.displayName}\n本机选择时间未获服务端证实\n"
        if (p == null) return source + (review.error ?: "预检未完成")
        return source + "数据 ${p.totalRows} 行 · 可用 ${p.validRows} 包 · ${p.productCount} 个产品\n" +
            "错误 ${p.errorRows} 行 · 完全重复 ${p.duplicateRows} 行 · 空行 ${p.ignoredRows} 行\n" +
            "可用重量 ${p.totalWeightGrams} 克" + if (p.bomRemoved) "\n已识别并移除文件头BOM" else ""
    }
    fun filtered(review: IndependentSkuReview?, issuesOnly: Boolean): List<IndependentSkuRow> =
        review?.preview?.rows.orEmpty().let { if (issuesOnly) it.filter { row -> row.errorCode != null } else it }
    fun rows(review: IndependentSkuReview?, page: Int, issuesOnly: Boolean): String {
        val rows = filtered(review, issuesOnly)
        if (rows.isEmpty()) return review?.error.orEmpty()
        val start = page * 25
        return "明细 ${start + 1}–${minOf(start + 25, rows.size)} / ${rows.size}\n\n" +
            rows.drop(start).take(25).joinToString("\n\n") { row ->
                val bale = row.parsedFields
                "原第 ${row.lineNo} 行 · ${row.errorDetail ?: "可用"}\n" +
                    (bale?.let { "${it.barcode} · ${it.model} · ${it.name}\n原重量 ${it.rawWeight} kg / ${it.weightGrams} 克" }
                        ?: "原文：${visible(row.rawText)}")
            }
    }
    private fun visible(text: String) = text.map { if (Character.isISOControl(it)) "\\u%04X".format(it.code) else it.toString() }.joinToString("")
}
