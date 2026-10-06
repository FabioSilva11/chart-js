package br.com.murupi.comanda

import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

/** Valores em centavos. */
fun money(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0).replace(' ', ' ')

/** "20", "20,5", "20.50", "R$ 20,00" -> centavos. */
fun parseMoney(text: String): Long? {
    val clean = text.replace("R$", "").replace(" ", "").trim()
    if (clean.isEmpty()) return null
    val normalized = if (clean.contains(',')) clean.replace(".", "").replace(',', '.') else clean
    val value = normalized.toDoubleOrNull() ?: return null
    return Math.round(value * 100)
}

fun centsToInput(cents: Long?): String =
    if (cents == null) "" else String.format(Locale("pt", "BR"), "%.2f", cents / 100.0)

data class Product(
    val id: String = newId(),
    val name: String,
    val category: String,
    val price: Long?,              // null = sem preço definido (pergunta na hora)
    val sides: List<String> = emptyList(), // acompanhamentos que podem ser retirados
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("category", category)
        put("price", price ?: JSONObject.NULL)
        put("sides", JSONArray(sides))
    }

    companion object {
        fun fromJson(o: JSONObject) = Product(
            id = o.getString("id"),
            name = o.getString("name"),
            category = o.getString("category"),
            price = if (o.isNull("price")) null else o.getLong("price"),
            sides = o.optJSONArray("sides").toStringList(),
        )
    }
}

data class OrderItem(
    val id: String = newId(),
    val productId: String,
    val name: String,
    val price: Long,
    val qty: Int,
    val obs: String = "",
    val sent: Boolean = false,     // já foi impresso na cozinha
) {
    val total get() = price * qty

    fun toJson() = JSONObject().apply {
        put("id", id); put("productId", productId); put("name", name)
        put("price", price); put("qty", qty); put("obs", obs); put("sent", sent)
    }

    companion object {
        fun fromJson(o: JSONObject) = OrderItem(
            id = o.getString("id"), productId = o.optString("productId"),
            name = o.getString("name"), price = o.getLong("price"), qty = o.getInt("qty"),
            obs = o.optString("obs"), sent = o.optBoolean("sent"),
        )
    }
}

enum class OrderType(val label: String) { MESA("Mesa"), BALCAO("Balcão"), COMANDA("Comanda") }

data class Order(
    val id: String = newId(),
    val type: OrderType,
    val number: Int,               // número da mesa / balcão / comanda
    val sub: Int,                  // subdivisão: 9.1, 9.2 ...
    val customer: String = "",
    val people: Int = 1,
    val items: List<OrderItem> = emptyList(),
    val billPrinted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val code get() = "$number.$sub"
    val title get() = "${type.label} $code"
    val subtotal get() = items.sumOf { it.total }

    fun toJson() = JSONObject().apply {
        put("id", id); put("type", type.name); put("number", number); put("sub", sub)
        put("customer", customer); put("people", people); put("billPrinted", billPrinted)
        put("createdAt", createdAt)
        put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject) = Order(
            id = o.getString("id"), type = OrderType.valueOf(o.getString("type")),
            number = o.getInt("number"), sub = o.getInt("sub"),
            customer = o.optString("customer"), people = o.optInt("people", 1),
            billPrinted = o.optBoolean("billPrinted"), createdAt = o.optLong("createdAt"),
            items = o.optJSONArray("items").toObjList { OrderItem.fromJson(it) },
        )
    }
}

data class ClosedOrder(
    val order: Order,
    val service: Long,
    val discount: Long,
    val total: Long,
    val payment: String,
    val closedAt: Long = System.currentTimeMillis(),
) {
    fun toJson() = JSONObject().apply {
        put("order", order.toJson()); put("service", service); put("discount", discount)
        put("total", total); put("payment", payment); put("closedAt", closedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = ClosedOrder(
            order = Order.fromJson(o.getJSONObject("order")), service = o.optLong("service"),
            discount = o.optLong("discount"), total = o.getLong("total"),
            payment = o.optString("payment"), closedAt = o.optLong("closedAt"),
        )
    }
}

enum class PrinterRole(val label: String) { COZINHA("Cozinha"), CAIXA("Caixa / Espelho"), AMBOS("Cozinha e Caixa") }

data class Printer(
    val id: String = newId(),
    val name: String,
    val host: String,
    val port: Int = 9100,
    val role: PrinterRole = PrinterRole.AMBOS,
    val columns: Int = 48,         // 48 = 80mm, 32 = 58mm
    val lastUsed: Long = 0,
    val lastStatus: String = "",
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("host", host); put("port", port)
        put("role", role.name); put("columns", columns); put("lastUsed", lastUsed)
        put("lastStatus", lastStatus)
    }

    companion object {
        fun fromJson(o: JSONObject) = Printer(
            id = o.getString("id"), name = o.getString("name"), host = o.getString("host"),
            port = o.optInt("port", 9100), role = PrinterRole.valueOf(o.optString("role", "AMBOS")),
            columns = o.optInt("columns", 48), lastUsed = o.optLong("lastUsed"),
            lastStatus = o.optString("lastStatus"),
        )
    }
}

data class PrintLog(
    val at: Long = System.currentTimeMillis(),
    val printer: String,
    val what: String,
    val ok: Boolean,
    val message: String,
) {
    fun toJson() = JSONObject().apply {
        put("at", at); put("printer", printer); put("what", what); put("ok", ok); put("message", message)
    }

    companion object {
        fun fromJson(o: JSONObject) = PrintLog(
            at = o.getLong("at"), printer = o.optString("printer"), what = o.optString("what"),
            ok = o.optBoolean("ok"), message = o.optString("message"),
        )
    }
}

data class Settings(
    val restaurant: String = "RESTAURANTE MURUPI",
    val tables: Int = 60,
    val serviceFee: Boolean = false,  // 10% de serviço
    val nextBalcao: Int = 1,
) {
    fun toJson() = JSONObject().apply {
        put("restaurant", restaurant); put("tables", tables)
        put("serviceFee", serviceFee); put("nextBalcao", nextBalcao)
    }

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) Settings() else Settings(
            restaurant = o.optString("restaurant", "RESTAURANTE MURUPI"),
            tables = o.optInt("tables", 60), serviceFee = o.optBoolean("serviceFee"),
            nextBalcao = o.optInt("nextBalcao", 1),
        )
    }
}

fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

fun <T> JSONArray?.toObjList(f: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).map { f(getJSONObject(it)) }

/** Observações rápidas oferecidas em todo item. */
val QUICK_OBS = listOf(
    "Sem cebola", "Sem tempero", "Sem pimenta", "Sem sal", "Pouco sal", "Sem alho",
    "Bem passado", "Ao ponto", "Mal passado", "Sem gelo", "Com gelo", "Sem açúcar",
    "Com leite", "Para viagem", "Separado",
)

/** Cardápio atual do Restaurante Murupi (fotos do cardápio físico). */
object Catalog {
    const val PRATOS = "PRATOS MURUPI"
    const val ISCAS = "ISCAS"
    const val PORCOES = "PORÇÕES"
    const val TIRA_GOSTO = "TIRA GOSTO"
    const val SOPAS = "SOPAS E CALDOS"
    const val DOCES = "DOCES RESTAURANTE"
    const val SUCOS = "SUCOS"
    const val BEBIDAS = "BEBIDAS RESTAURANTE"

    private val ACOMP_FRITA = listOf("Arroz branco", "Macarrão", "Farofa", "Maionese", "Salada crua", "Batata frita")
    private val ACOMP_PALHA = listOf("Arroz branco", "Macarrão", "Farofa", "Maionese", "Salada crua", "Batata palha")
    private val ACOMP_FAROFA = listOf("Arroz", "Macarrão", "Maionese", "Salada")
    private val ACOMP_CAMARAO = listOf("Arroz", "Macarrão", "Maionese", "Farofa", "Batata frita")

    private fun p(name: String, cat: String, reais: Int?, sides: List<String> = emptyList()) =
        Product(id = "p" + name.lowercase().filter { it.isLetterOrDigit() }.take(24), name = name,
            category = cat, price = reais?.let { it * 100L }, sides = sides)

    fun defaults(): List<Product> = listOf(
        p("Carne de Sol", PRATOS, 23, ACOMP_FRITA),
        p("Strogonoff de Carne", PRATOS, 20, ACOMP_FRITA),
        p("Strogonoff de Frango", PRATOS, 18, ACOMP_PALHA),
        p("Creme de Camarão", PRATOS, 20, ACOMP_CAMARAO),
        p("Macarrão ao Molho de Camarão", PRATOS, 20),
        p("Lasanha de Carne", PRATOS, 20, ACOMP_FRITA),
        p("Lasanha de Frango", PRATOS, 20, ACOMP_FRITA),
        p("Farofa de Jabá com Banana", PRATOS, 20, ACOMP_FAROFA),
        p("Farofa de Carne Seca", PRATOS, 20, ACOMP_FAROFA),
        p("Picanha Suína", PRATOS, 25, listOf("Arroz branco", "Feijão tropeiro", "Macarrão", "Farofa", "Maionese", "Salada crua", "Macaxeira frita")),
        p("Picanha Chapeada", PRATOS, 40, listOf("Feijão tropeiro", "Macarrão", "Farofa", "Salada crua", "Maionese", "Fritas")),
        p("Frango Chapeado", PRATOS, 16, ACOMP_FRITA),
        p("Bife Acebolado", PRATOS, 20, ACOMP_FRITA),
        p("Bife a Cavalo", PRATOS, 22, ACOMP_FRITA),
        p("Costela Desfiada", PRATOS, 20, ACOMP_FRITA),
        p("Fricassê", PRATOS, 20, ACOMP_PALHA),

        p("Isca de Carne", ISCAS, 20, ACOMP_FRITA),
        p("Isca de Frango", ISCAS, 16, ACOMP_FRITA),
        p("Isca Mista", ISCAS, 20, ACOMP_FRITA),

        p("Bolinho de Pirarucu", TIRA_GOSTO, 1),
        p("Dadinho de Tapioca", TIRA_GOSTO, 1),
        p("Bolinha de Queijo", TIRA_GOSTO, 1),

        p("Porção Lasanha", PORCOES, 15),
        p("Porção Batata", PORCOES, 5),
        p("Porção Farofa de Carne Seca", PORCOES, 15),
        p("Porção Farofa de Jabá", PORCOES, 15),
        p("Porção Maionese", PORCOES, 5),
        p("Porção Feijão Tropeiro", PORCOES, 5),

        p("Galinha Caipira", SOPAS, 15),
        p("Sopa de Abóbora", SOPAS, 10),

        p("Torta de Maracujá", DOCES, null),
        p("Torta de Cupuaçu", DOCES, null),
        p("Torta de Limão", DOCES, null),
        p("Torta Sonho de Valsa", DOCES, null),
        p("Torta Prestígio", DOCES, null),
        p("Torta de Chocolate", DOCES, null),
        p("Pudim", DOCES, null),

        p("Suco Copo 300ml", SUCOS, 6, listOf("Maracujá", "Goiaba", "Cupuaçu", "Graviola")),
        p("Suco Copo 400ml", SUCOS, null, listOf("Maracujá", "Goiaba", "Cupuaçu", "Graviola")),
        p("Suco Copo 500ml", SUCOS, 10, listOf("Maracujá", "Goiaba", "Cupuaçu", "Graviola")),

        p("Água", BEBIDAS, null),
        p("Água com Gás", BEBIDAS, null),
        p("Água Tônica", BEBIDAS, null),
        p("Refrigerante Lata", BEBIDAS, null),
        p("Refrigerante 1L", BEBIDAS, null),
    )

    /** Em sucos, as "opções" são sabores (escolhe um), nos pratos são acompanhamentos (retira). */
    fun sidesAreFlavors(p: Product) = p.category == SUCOS
}
