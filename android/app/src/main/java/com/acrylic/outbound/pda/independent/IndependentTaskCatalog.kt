package com.acrylic.outbound.pda.independent

internal object IndependentTaskCatalog {
    fun products(review: IndependentSkuReview): List<IndependentSkuBale> {
        check(review.decision?.outcome == "ACTIVATED") { "任务须引用已启用的原资料" }
        val preview = checkNotNull(review.preview)
        check(preview.errorRows == 0 && (preview.duplicateRows == 0 || review.decision.duplicatesConfirmed))
        return preview.rows.filter { it.errorCode == null }.map { checkNotNull(it.parsedFields) }
            .distinctBy { it.deliveryCode }.sortedBy { it.deliveryCode }
    }
    fun lines(command: IndependentTaskCommand, review: IndependentSkuReview): List<IndependentTaskLine> {
        val products = products(review).associateBy { it.deliveryCode }
        return command.quantities.toSortedMap().map { (code, count) ->
            val product = checkNotNull(products[code]) { "计划产品 $code 不在原资料中" }
            IndependentTaskLine(command.id, code, product.model, product.name, count)
        }
    }
}
