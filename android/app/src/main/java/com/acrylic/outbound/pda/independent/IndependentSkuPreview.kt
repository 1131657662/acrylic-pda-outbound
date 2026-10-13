package com.acrylic.outbound.pda.independent

data class IndependentSkuBale(val model: String, val name: String, val barcode: String, val deliveryCode: String,
    val rawWeight: String, val weightGrams: String)

data class IndependentSkuRow(val lineNo: Int, val rawText: String, val parsedFields: IndependentSkuBale?,
    val errorCode: String? = null, val errorDetail: String? = null) {
    val blocking: Boolean get() = errorCode != null && errorCode != "DUPLICATE_IDENTICAL"
}

data class IndependentSkuPreview(val parserVersion: String, val status: String, val totalRows: Int, val validRows: Int,
    val errorRows: Int, val duplicateRows: Int, val ignoredRows: Int, val productCount: Int,
    val totalWeightGrams: String, val bomRemoved: Boolean, val rows: List<IndependentSkuRow>)

class IndependentSkuException(val code: String, message: String) : IllegalArgumentException(message)
