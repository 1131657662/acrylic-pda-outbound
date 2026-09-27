package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.retention.AuditArchiveRepository
import com.acrylic.outbound.pda.retention.SubmissionArchiveRepository
import org.json.JSONObject
import java.util.concurrent.CancellationException

class BackgroundSync(private val orders: SyncRepository, private val barcodes: BarcodeRepository,
    private val audit: AuditRepository, private val archives: AuditArchiveRepository,
    private val submissions: SubmissionArchiveRepository,
    private val bindings: com.acrylic.outbound.pda.temporary.SourceBindingRepository,
    private val current: () -> JSONObject) {
    fun run(control: SyncRun): List<SyncOutcome> {
        val auth = JSONObject(current().toString())
        return listOf<() -> SyncResult>(
            { orders.syncOrders(run = control) }, { barcodes.syncBarcodes(run = control) },
            { syncAudit(control) },
            { submissions.refreshBackground(control) },
            // A controlled link changes the canonical order on the server; the device only learns it here.
            { bindings.installBindings(run = control) }
        ).map { action ->
            try { control.identity(auth, current); SyncOutcome(result = action()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { SyncOutcome(error = error) }
        }
    }

    private fun syncAudit(control: SyncRun): SyncResult {
        if (!PdaCapabilities.audit(current())) return SyncResult("当前人员无审计补传权限，原记录保留", supported = false)
        val restored = try { archives.restore(control) }
        catch (error: ApiFailure) {
            if (error.httpStatus == 404) return SyncResult("当前后台尚未支持审计恢复，原记录保留", supported = false)
            throw error
        }
        if (!restored) return SyncResult("历史操作记录仍在恢复", more = true)
        val uploaded = audit.syncAudit(control)
        if (!uploaded.supported) return uploaded
        val archived = archives.syncArchive(control)
        return archived.copy(more = uploaded.more || archived.more)
    }
}

data class SyncOutcome(val result: SyncResult? = null, val error: Exception? = null)
