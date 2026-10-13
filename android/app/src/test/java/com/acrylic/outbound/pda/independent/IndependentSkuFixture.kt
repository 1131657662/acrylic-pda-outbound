package com.acrylic.outbound.pda.independent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.TimeSample
import java.nio.charset.Charset

internal class IndependentSkuFixture : AutoCloseable {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var db: PdaDatabase
    var time = TimeSample(1000, 100000, 7)
    val repository get() = IndependentSkuRepository(db) { time }
    init { context.deleteDatabase("outbound-pda.db"); db = PdaDatabase.open(context) }
    fun reopen() { db.close(); db = PdaDatabase.open(context) }
    fun bytes(barcode: String = "003A0000001", weight: String = "15.501"): ByteArray =
        ("MODEL".padEnd(20) + "NAME".padEnd(14) + barcode.padEnd(13) + weight.padEnd(10)).toByteArray(Charset.forName("GB18030"))
    fun stage(barcode: String = "003A0000001") = repository.stage(bytes(barcode), "sku.txt")
    fun activate(barcode: String = "003A0000001"): IndependentSkuReview {
        val review = stage(barcode)
        return repository.activate(review.imported.id, false, review.activeImportId)
    }
    override fun close() { db.close(); context.deleteDatabase("outbound-pda.db") }
}
