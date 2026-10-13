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

class RescueExportActivity : ComponentActivity() {
    private lateinit var binding: ActivityRescueExportBinding
    private val model: RescueExportViewModel by viewModels()
    private var identityLabels = emptyList<String>()
    private var orderLabels = emptyList<String>()
    private var packageIds = emptyList<String>()
    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) model.cancelled() else model.copy(uri)
    }
    private val openReceipt = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importReceipt(uri)
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
        binding.supervisorImport.setOnClickListener { RescueSupervisorImportDialog.show(this, model::importSupervisor) }
        binding.unlock.setOnClickListener {
            val password = binding.credential.text.toString().toCharArray(); binding.credential.text.clear()
            model.unlock(binding.identity.selectedItemPosition, password)
        }
        binding.readSnapshot.setOnClickListener { model.readSnapshot() }
        binding.importReceipt.setOnClickListener { openReceipt.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
        binding.nextPage.setOnClickListener { model.nextPage() }
        binding.lockScope.setOnClickListener { model.lock() }
        binding.recoverPackage.setOnClickListener { model.recover(binding.privatePackage.selectedItemPosition) }
        binding.generate.setOnClickListener {
            val password = binding.packagePassword.text.toString().toCharArray(); binding.packagePassword.text.clear()
            model.generate(password)
        }
        binding.copy.setOnClickListener { createDocument.launch("pda-${java.util.UUID.randomUUID()}.pdarescue") }
        binding.back.setOnClickListener { finish() }
    }
    override fun onResume() { super.onResume(); model.loadIdentities() }
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
            binding.order.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, orders)
            binding.order.setSelection(state.selected)
        }
        binding.status.text = state.message; binding.summary.text = state.summary; binding.copyState.text = state.copyState
        renderPackages(state); renderActions(state)
    }
    private fun renderActions(state: RescueExportUi) {
        binding.credentialsPanel.visibility = if (state.unlocked) View.GONE else View.VISIBLE
        binding.lockScope.visibility = if (state.unlocked) View.VISIBLE else View.GONE
        binding.lockScope.isEnabled = !state.busy
        binding.unlock.isEnabled = !state.busy; binding.credentialSetup.isEnabled = !state.busy
        binding.supervisorRequest.isEnabled = !state.busy
        binding.supervisorImport.isEnabled = !state.busy
        binding.identity.isEnabled = !state.busy; binding.order.isEnabled = !state.busy
        binding.readSnapshot.isEnabled = !state.busy && state.unlocked && state.orders.isNotEmpty()
        binding.importReceipt.isEnabled = !state.busy && state.unlocked && state.orders.isNotEmpty()
        binding.generate.isEnabled = !state.busy && state.canGenerate; binding.copy.isEnabled = !state.busy && state.canCopy
        binding.nextPage.visibility = if (state.hasMore) View.VISIBLE else View.GONE
        binding.nextPage.isEnabled = !state.busy
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
