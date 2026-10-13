package com.acrylic.outbound.pda.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.security.DeviceKeys
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Base64

data class RescueExportUi(val busy: Boolean = false, val message: String = "请核验已预置的本地救援凭据",
    val identities: List<RescueCredentialChoice> = emptyList(), val orders: List<RescueOrderChoice> = emptyList(),
    val hasMore: Boolean = false, val selected: Int = 0, val summary: String = "", val unlocked: Boolean = false,
    val privatePackages: List<RescuePrivatePackage> = emptyList(),
    val canGenerate: Boolean = false, val canCopy: Boolean = false,
    val copyState: String = "电脑副本尚未核验；生成本机包不代表已外置备份。")

class RescueExportViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as PdaApplication
    private val credentials get() = RescueCredentials.shared(app, app.session)
    private val files = RescueExportFiles(app)
    private var grant: RescueReadGrant? = null
    private var snapshot: RescueSnapshot? = null
    private var generated: RescueGeneratedFile? = null
    private var pageCursor: String? = null
    private val mutable = MutableStateFlow(RescueExportUi())
    val state: StateFlow<RescueExportUi> = mutable.asStateFlow()
    fun prefer(source: String, id: String) {
        if (source in setOf("FORMAL", "TEMP") && id.isNotBlank()) {
            saved["preferredSource"] = source; saved["preferredId"] = id
        }
    }
    fun loadIdentities() = run(state.value.message) { mutable.update { it.copy(identities = credentials.choices()) } }
    fun importSupervisor(text: String, password: CharArray) {
        if (state.value.busy) { password.fill('\u0000'); return }
        lock()
        run("正在核验主管签名原件") {
            try {
                val message = credentials.importSupervisor(text, password)
                mutable.update { it.copy(identities = credentials.choices(), message = message) }
            } finally { password.fill('\u0000') }
        }
    }
    fun lock() {
        if (state.value.busy) return
        grant = null; snapshot = null; generated = null
        mutable.update { RescueExportUi(identities = it.identities) }
    }
    fun unlock(index: Int, password: CharArray) {
        if (state.value.busy) { password.fill('\u0000'); return }
        grant = null; snapshot = null; generated = null
        mutable.update { it.copy(unlocked = false, canGenerate = false, canCopy = false, privatePackages = emptyList()) }
        run("正在核验本地只读范围") {
            try {
                val identity = state.value.identities.getOrNull(index) ?: error("本机没有可用预置凭据，请在联网时预置")
                grant = credentials.unlockChoice(identity.id, password)
                page("")
                val packages = files.available(checkNotNull(grant))
                mutable.update { it.copy(unlocked = true, privatePackages = packages, summary = "",
                    message = "只读范围核验通过；不授扫码或放行权。") }
            } finally { password.fill('\u0000') }
        }
    }
    fun recover(index: Int) = run("正在回读原私有包") {
        val candidate = state.value.privatePackages.getOrNull(index) ?: error("没有可恢复的原私有包")
        generated = null; mutable.update { it.copy(canCopy = false) }
        val result = files.recover(candidate.id, checkNotNull(grant))
        generated = result
        mutable.update { it.copy(canCopy = true, summary = result.summary,
            copyState = "原私有包回读通过；目标与电脑副本尚未核验。") }
    }
    fun select(index: Int) {
        if (index == state.value.selected) return
        snapshot = null; generated = null
        mutable.update { it.copy(selected = index, canGenerate = false, canCopy = false, summary = "") }
    }
    fun nextPage() = run("正在读取下一页原订单") {
        page(pageCursor ?: return@run)
    }
    private fun page(after: String) {
        val rows = RescueOrders(app.database).page(checkNotNull(grant), after)
        pageCursor = rows.nextCursor
        val preferred = saved.get<String>("preferredId")
        val source = saved.get<String>("preferredSource")
        val options = rows.rows.toMutableList()
        if (after.isEmpty() && preferred != null && source != null && options.none { it.target.localOrderId == preferred && it.target.source == source })
            options.add(0, RescueOrderChoice(RescueTarget(source, preferred), "原页面订单 · $preferred"))
        val selected = if (after.isEmpty()) options.indexOfFirst { it.target.localOrderId == preferred && it.target.source == source }.coerceAtLeast(0) else 0
        snapshot = null; generated = null
        mutable.update { it.copy(orders = options, hasMore = rows.nextCursor != null, selected = selected, canGenerate = false, canCopy = false) }
    }
    fun readSnapshot() = run("正在读取一致原件快照") {
        snapshot = null; generated = null
        mutable.update { it.copy(canGenerate = false, canCopy = false) }
        val selected = state.value.orders.getOrNull(state.value.selected) ?: error("请选取原订单")
        snapshot = RescueRoomSnapshot(app.database).read(selected.target, checkNotNull(grant))
        val facts = RescueFacts.summary(checkNotNull(snapshot))
        mutable.update { it.copy(canGenerate = true, summary = selected.label + "\n有效 ${facts.getString("active")} · VOID ${facts.getString("void")} · " +
            "处置 ${facts.getString("disposed")} · 未决 ${facts.getString("pending")} · 失败 ${facts.getString("failed")}\n" +
            if ("submission.json" in checkNotNull(snapshot).originals) "存在原冻结批次，正文按原字节保存" else "未冻结；不会补造提交批次") }
    }
    fun generate(password: CharArray) {
        if (state.value.busy) { password.fill('\u0000'); return }
        run("正在生成加密应急包，原记录保留") {
            try {
                val keys = DeviceKeys(); val public = keys.publicKey(false).also(app.session.settings::verifyKey)
                val signer = RescueSigner(Base64.getDecoder().decode(public), keys::sign)
                val result = files.generate(checkNotNull(snapshot), checkNotNull(grant) to signer, password)
                generated = result
                mutable.update { it.copy(canCopy = true, summary = result.summary, privatePackages = files.available(checkNotNull(grant)),
                    copyState = "本机包生成并回读通过。目标未写入；电脑副本尚未核验。") }
            } finally { password.fill('\u0000') }
        }
    }
    fun cancelled() { mutable.update { it.copy(message = "目标选择已取消，原私有包保留；未新增已核验外置副本。") } }
    fun importReceipt(uri: Uri) = run("正在保全并核验原批次中转回执") {
        val selected = state.value.orders.getOrNull(state.value.selected) ?: error("请选择原冻结订单")
        val bytes = checkNotNull(app.contentResolver.openInputStream(uri)).use { it.readBytesBounded() }
        val message = com.acrylic.outbound.pda.relay.RelayReceiptImport(app).apply(bytes, selected.target, checkNotNull(grant))
        snapshot = null; generated = null
        mutable.update { it.copy(message = message, canGenerate = false, canCopy = false) }
    }
    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer); if (count < 0) break
            check(count > 0 && output.size() + count <= 262144) { "中转回执文件过大或无法完整读取" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    fun copy(uri: Uri) = run("正在写入目标并回读，完成前不要拔出介质") {
        val digest = files.copy(checkNotNull(generated), uri, checkNotNull(grant))
        mutable.update { it.copy(copyState = "目标写入与回读一致。SHA256 $digest\n电脑副本尚未核验；请另行保管解密口令。") }
    }
    private fun run(message: String, action: () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, message = message) }
        viewModelScope.launch(Dispatchers.IO) {
            try { action() }
            catch (error: Exception) { mutable.update { it.copy(message = error.message ?: "未完成，请保留原资料", copyState = "本次操作未完成，原资料/私有包保留；不要使用半文件。电脑副本尚未核验。") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
