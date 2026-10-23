package com.acrylic.outbound.pda.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.*
import com.acrylic.outbound.pda.databinding.ActivityRescueExportBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class RescueExportActivity : ComponentActivity() {
    private lateinit var binding: ActivityRescueExportBinding
    private val model: RescueExportViewModel by viewModels()
    private var identityLabels = emptyList<String>()
    private var orderLabels = emptyList<String>()
    private var packageIds = emptyList<String>()
    private var readingSupervisor = false
    private var supervisorSetupActive = false
    private var packageSetupActive = false
    private val packageSetup = RescuePackageSetupFlow(this, { model }) {
        packageSetupActive = it
        if (::binding.isInitialized) renderActions(model.state.value)
    }
    private val generatedSupervisor = SupervisorRescueSetupFlow(this, { model.loadIdentities(it) }, {
        supervisorSetupActive = it
        if (::binding.isInitialized) renderActions(model.state.value)
    })
    private val ownCard = OwnRescueCardPicker(this, { model }, { binding.identity.selectedItemPosition })
    private val supervisorCard = SupervisorRescueCardPicker(this, { model }, { binding.identity.selectedItemPosition })
    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) model.cancelled() else model.copy(uri)
    }
    private val createTextDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) model.cancelTextExport() else model.exportText(uri)
    }
    private val openReceipt = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importReceipt(uri)
    }
    private val openSupervisor = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readSupervisorMaterial(uri)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityRescueExportBinding.inflate(layoutInflater); setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        model.prefer(intent.getStringExtra("source").orEmpty(), intent.getStringExtra("localOrderId").orEmpty())
        actions(); selection()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) } }
    }
    private fun actions() {
        binding.credentialSetup.setOnClickListener { startActivity(Intent(this, RescueCredentialActivity::class.java)) }
        binding.supervisorRequest.setOnClickListener { startActivity(Intent(this, RescueRequestActivity::class.java)) }
        binding.supervisorImport.setOnClickListener { showSupervisorImport() }
        binding.supervisorFileImport.setOnClickListener { openSupervisor.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
        binding.unlock.setOnClickListener {
            val password = binding.credential.text.toString().toCharArray(); binding.credential.text.clear()
            model.unlock(binding.identity.selectedItemPosition, password)
        }
        binding.ownCardUnlock.setOnClickListener { ownCard.choose() }
        binding.supervisorCardUnlock.setOnClickListener { supervisorCard.choose() }
        binding.readSnapshot.setOnClickListener { model.readSnapshot() }
        binding.exportText.setOnClickListener { if (model.beginTextExport()) createTextDocument.launch("out.txt") }
        binding.importReceipt.setOnClickListener { openReceipt.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
        binding.nextPage.setOnClickListener { model.nextPage() }
        binding.lockScope.setOnClickListener { model.lock() }
        binding.recoverPackage.setOnClickListener { model.recover(binding.privatePackage.selectedItemPosition) }
        binding.generate.setOnClickListener {
            val password = binding.packagePassword.text.toString().toCharArray(); binding.packagePassword.text.clear()
            model.generate(password)
        }
        binding.generateSecurePackage.setOnClickListener { packageSetup.show() }
        binding.copy.setOnClickListener { createDocument.launch("pda-${java.util.UUID.randomUUID()}.pdarescue") }
        binding.back.setOnClickListener { finish() }
    }
    override fun onResume() { super.onResume(); model.loadIdentities() }
    private fun readSupervisorMaterial(uri: android.net.Uri) {
        if (readingSupervisor || supervisorSetupActive || packageSetupActive || model.state.value.busy) return
        readingSupervisor = true
        binding.supervisorFileImport.isEnabled = false
        lifecycleScope.launch {
            try {
                val material = withContext(Dispatchers.IO) {
                    SupervisorMaterialFile.read(checkNotNull(contentResolver.openInputStream(uri)))
                }
                if (supervisorSetupActive || packageSetupActive || model.state.value.busy) {
                    binding.status.text = "当前操作尚未结束，主管材料未导入；结束后请重新选择。"
                    return@launch
                }
                showSupervisorImport(material)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                binding.status.text = "主管材料未读取，尚未导入。请选择完整的UTF-8原件（不超过128 KiB），原资料保留。"
            } finally {
                readingSupervisor = false
                renderActions(model.state.value)
            }
        }
    }
    override fun onDestroy() { packageSetup.close(); generatedSupervisor.close(); super.onDestroy() }
    private fun showSupervisorImport(material: String = "") {
        if (supervisorSetupActive || packageSetupActive || model.state.value.busy) return
        RescueSupervisorImportDialog.show(this, model::importSupervisor, material) {
            model.lock(); generatedSupervisor.show(it)
        }
    }
    private fun selection() {
        binding.order.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { model.select(position) }
        }
    }
    private fun render(state: RescueExportUi) {
        val identities = state.identities.map { it.label }
        if (identities != identityLabels) {
            identityLabels = identities
            binding.identity.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, identities)
        }
        val orders = state.orders.map { it.label }
        if (orders != orderLabels) {
            orderLabels = orders
            binding.order.adapter = ArrayAdapter(this, com.acrylic.outbound.pda.R.layout.item_rescue_order, orders).apply {
                setDropDownViewResource(com.acrylic.outbound.pda.R.layout.item_rescue_order)
            }
            binding.order.setSelection(state.selected)
        }
        binding.status.text = state.message; binding.summary.text = state.summary
        binding.textExportState.text = state.textExportState; binding.copyState.text = state.copyState
        renderPackages(state); renderActions(state)
    }
    private fun renderActions(state: RescueExportUi) {
        binding.credentialsPanel.visibility = if (state.unlocked) View.GONE else View.VISIBLE
        binding.lockScope.visibility = if (state.unlocked) View.VISIBLE else View.GONE
        binding.lockScope.isEnabled = !state.busy
        binding.unlock.isEnabled = !state.busy; binding.credentialSetup.isEnabled = !state.busy
        binding.ownCardUnlock.isEnabled = !state.busy && !ownCard.pending
        binding.supervisorCardUnlock.isEnabled = !state.busy && !supervisorCard.pending
        binding.supervisorRequest.isEnabled = !state.busy
        binding.supervisorImport.isEnabled = !state.busy
        binding.supervisorFileImport.isEnabled = !state.busy && !readingSupervisor
        binding.identity.isEnabled = !state.busy; binding.order.isEnabled = !state.busy
        binding.readSnapshot.isEnabled = !state.busy && state.unlocked && state.orders.isNotEmpty()
        binding.exportText.isEnabled = !state.busy && state.canExportText
        binding.importReceipt.isEnabled = !state.busy && state.unlocked &&
            state.orders.getOrNull(state.selected)?.target?.source in setOf("FORMAL", "TEMP")
        binding.generate.isEnabled = !state.busy && state.canGenerate; binding.copy.isEnabled = !state.busy && state.canCopy
        binding.generateSecurePackage.isEnabled = !state.busy && state.canGenerate && !packageSetupActive && !supervisorSetupActive
        binding.nextPage.visibility = if (state.hasMore) View.VISIBLE else View.GONE
        binding.nextPage.isEnabled = !state.busy
        if (supervisorSetupActive || packageSetupActive) listOf(binding.unlock, binding.credentialSetup, binding.ownCardUnlock, binding.supervisorCardUnlock,
            binding.supervisorRequest, binding.supervisorImport, binding.supervisorFileImport, binding.identity,
            binding.order, binding.readSnapshot, binding.exportText, binding.importReceipt, binding.generate, binding.generateSecurePackage, binding.copy,
            binding.nextPage, binding.lockScope, binding.recoverPackage).forEach { it.isEnabled = false }
    }
    private fun renderPackages(state: RescueExportUi) {
        val ids = state.privatePackages.map { it.id }
        if (ids != packageIds) {
            packageIds = ids
            val labels = state.privatePackages.map { it.summary.substringBefore('\n') + " · " + it.id }
            binding.privatePackage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        }
        binding.privatePackage.isEnabled = !state.busy && state.unlocked
        binding.recoverPackage.isEnabled = !state.busy && state.unlocked && ids.isNotEmpty()
    }
}
