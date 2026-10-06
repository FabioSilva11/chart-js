package br.com.murupi.comanda

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Estado do app, salvo em JSON no armazenamento interno a cada alteração. */
class Store(context: Context) {
    private val file = File(context.filesDir, "comanda.json")

    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var orders by mutableStateOf<List<Order>>(emptyList()); private set
    var closed by mutableStateOf<List<ClosedOrder>>(emptyList()); private set
    var printers by mutableStateOf<List<Printer>>(emptyList()); private set
    var printLog by mutableStateOf<List<PrintLog>>(emptyList()); private set
    var settings by mutableStateOf(Settings()); private set

    init { load() }

    private fun load() {
        val o = try { if (file.exists()) JSONObject(file.readText()) else null } catch (e: Exception) { null }
        if (o == null) {
            products = Catalog.defaults()
            save(); return
        }
        products = o.optJSONArray("products").toObjList { Product.fromJson(it) }.ifEmpty { Catalog.defaults() }
        orders = o.optJSONArray("orders").toObjList { Order.fromJson(it) }
        closed = o.optJSONArray("closed").toObjList { ClosedOrder.fromJson(it) }
        printers = o.optJSONArray("printers").toObjList { Printer.fromJson(it) }
        printLog = o.optJSONArray("printLog").toObjList { PrintLog.fromJson(it) }
        settings = Settings.fromJson(o.optJSONObject("settings"))
    }

    private fun save() {
        val o = JSONObject().apply {
            put("products", JSONArray().apply { products.forEach { put(it.toJson()) } })
            put("orders", JSONArray().apply { orders.forEach { put(it.toJson()) } })
            put("closed", JSONArray().apply { closed.forEach { put(it.toJson()) } })
            put("printers", JSONArray().apply { printers.forEach { put(it.toJson()) } })
            put("printLog", JSONArray().apply { printLog.forEach { put(it.toJson()) } })
            put("settings", settings.toJson())
        }
        val tmp = File(file.parentFile, "comanda.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    // ---------- Catálogo ----------
    val categories: List<String> get() = products.map { it.category }.distinct().sorted()

    fun upsertProduct(p: Product) {
        products = if (products.any { it.id == p.id }) products.map { if (it.id == p.id) p else it } else products + p
        save()
    }

    fun deleteProduct(id: String) { products = products.filterNot { it.id == id }; save() }

    fun resetCatalog() { products = Catalog.defaults(); save() }

    // ---------- Pedidos ----------
    fun order(id: String) = orders.firstOrNull { it.id == id }

    fun ordersOf(type: OrderType, number: Int) =
        orders.filter { it.type == type && it.number == number }.sortedBy { it.sub }

    fun openOrder(type: OrderType, number: Int, customer: String = ""): Order {
        val sub = (ordersOf(type, number).maxOfOrNull { it.sub } ?: 0) + 1
        val o = Order(type = type, number = number, sub = sub, customer = customer)
        orders = orders + o
        save()
        return o
    }

    fun newBalcao(customer: String): Order {
        val n = settings.nextBalcao
        settings = settings.copy(nextBalcao = n + 1)
        return openOrder(OrderType.BALCAO, n, customer)
    }

    fun updateOrder(o: Order) { orders = orders.map { if (it.id == o.id) o else it }; save() }

    fun removeOrder(id: String) { orders = orders.filterNot { it.id == id }; save() }

    fun addItem(orderId: String, item: OrderItem) {
        val o = order(orderId) ?: return
        updateOrder(o.copy(items = o.items + item, billPrinted = false))
    }

    fun updateItem(orderId: String, item: OrderItem) {
        val o = order(orderId) ?: return
        updateOrder(o.copy(items = o.items.map { if (it.id == item.id) item else it }))
    }

    fun removeItem(orderId: String, itemId: String) {
        val o = order(orderId) ?: return
        updateOrder(o.copy(items = o.items.filterNot { it.id == itemId }))
    }

    fun transferItem(fromId: String, itemId: String, toId: String) {
        val from = order(fromId) ?: return
        val to = order(toId) ?: return
        val item = from.items.firstOrNull { it.id == itemId } ?: return
        orders = orders.map {
            when (it.id) {
                fromId -> it.copy(items = it.items.filterNot { i -> i.id == itemId })
                toId -> it.copy(items = it.items + item)
                else -> it
            }
        }
        save()
    }

    fun markSent(orderId: String, itemIds: Set<String>) {
        val o = order(orderId) ?: return
        updateOrder(o.copy(items = o.items.map { if (it.id in itemIds) it.copy(sent = true) else it }))
    }

    fun finalize(orderId: String, service: Long, discount: Long, payment: String): ClosedOrder? {
        val o = order(orderId) ?: return null
        val c = ClosedOrder(o, service, discount, o.subtotal + service - discount, payment)
        closed = listOf(c) + closed
        orders = orders.filterNot { it.id == orderId }
        save()
        return c
    }

    fun clearHistory() { closed = emptyList(); save() }

    // ---------- Impressoras ----------
    fun upsertPrinter(p: Printer) {
        printers = if (printers.any { it.id == p.id }) printers.map { if (it.id == p.id) p else it } else printers + p
        save()
    }

    fun deletePrinter(id: String) { printers = printers.filterNot { it.id == id }; save() }

    fun printerFor(kitchen: Boolean): Printer? {
        val wanted = if (kitchen) PrinterRole.COZINHA else PrinterRole.CAIXA
        return printers.firstOrNull { it.role == wanted } ?: printers.firstOrNull { it.role == PrinterRole.AMBOS }
            ?: printers.firstOrNull()
    }

    fun logPrint(printer: Printer, what: String, ok: Boolean, message: String) {
        printLog = (listOf(PrintLog(printer = "${printer.name} (${printer.host})", what = what, ok = ok, message = message)) + printLog).take(200)
        printers = printers.map {
            if (it.id == printer.id) it.copy(lastUsed = System.currentTimeMillis(), lastStatus = if (ok) "OK" else message) else it
        }
        save()
    }

    fun clearPrintLog() { printLog = emptyList(); save() }

    fun updateSettings(s: Settings) { settings = s; save() }
}
