package com.acrylic.m71demo.fixtures

import android.content.Context
import com.acrylic.m71demo.data.*
import com.acrylic.m71demo.workflow.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class OrderCatalog @Inject constructor(private val store: DemoStore, private val importer: InventoryImporter,
    @ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("demo", Context.MODE_PRIVATE)

    fun initialize(): String {
        importer.ensureSample()
        if (store.db.audit().device() == null) store.db.audit().insertDevice(DeviceState(java.util.UUID.randomUUID().toString()))
        val previous = prefs.getString("currentOrder", null)?.let { store.db.orders().get(it) }
        return previous?.id ?: create(OrderRequest("测试车辆", "003:80,571:100"))
    }

    fun create(request: OrderRequest): String {
        require(request.vehicle.isNotBlank() && request.vehicle.length <= 32)
        val plans = parsePlans(request.plans)
        val auth = store.authorization.issue(store.device().id)
        val order = DemoOrder(java.util.UUID.randomUUID().toString(), request.vehicle,
            request.snapshot, auth.id, source = request.source, createdAt = store.clock.wallMs())
        store.db.runInTransaction {
            store.db.audit().insertAuthorization(auth)
            store.db.orders().insert(order)
            store.db.orders().insertLines(plans.map { OrderLine(order.id, it.first, it.second) })
            store.audit(order.id, null, AuditDetail("ORDER_CREATED", "测试单，各产品独立计划"))
        }
        select(order.id)
        return order.id
    }

    fun select(id: String) { prefs.edit().putString("currentOrder", id).apply() }

    fun renew(orderId: String) = store.db.runInTransaction {
        val order = requireNotNull(store.db.orders().get(orderId))
        check(store.db.captures().pendingCount(orderId) == 0) { "先恢复/处理旧确认，不覆盖历史授权" }
        val auth = store.authorization.issue(store.device().id)
        store.db.audit().insertAuthorization(auth)
        store.db.orders().update(order.copy(authorizationId = auth.id))
        store.audit(order.id, null, AuditDetail("DEMO_AUTH_RENEWED", "仅本地测试授权，非云端许可"))
    }

    companion object {
        fun parsePlans(text: String): List<Pair<String, Int>> {
            val plans = text.split(',', '，').map { raw ->
                val parts = raw.trim().split(':', '：')
                require(parts.size == 2 && parts[0].matches(Regex("[0-9]{3}"))) { "计划格式：003:80,571:100" }
                val count = parts[1].toIntOrNull() ?: error("计划包数必须为正整数")
                require(count in 1..1_000_000) { "单行计划须1～100万，属于测试数值保护" }
                parts[0] to count
            }
            require(plans.isNotEmpty() && plans.map { it.first }.distinct().size == plans.size) { "产品代码不能重复" }
            return plans
        }
    }
}

data class OrderRequest(val vehicle: String, val plans: String,
    val snapshot: String = InventoryImporter.SAMPLE_ID, val source: String = "TEMP_TEST")
