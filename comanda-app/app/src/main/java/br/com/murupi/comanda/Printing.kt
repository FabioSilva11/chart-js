package br.com.murupi.comanda

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Escopo que sobrevive ao fechamento de telas/diálogos (impressões em andamento). */
val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/** Montagem de cupons ESC/POS (compatível com a maioria das térmicas: Epson, Elgin, Bematech, Knup, genéricas). */
class EscPos(private val cols: Int) {
    private val out = ByteArrayOutputStream()

    init { out.write(byteArrayOf(0x1B, 0x40)) } // inicializa

    private fun raw(vararg b: Int) = apply { b.forEach { out.write(it) } }

    fun center() = raw(0x1B, 0x61, 1)
    fun left() = raw(0x1B, 0x61, 0)
    fun bold(on: Boolean) = raw(0x1B, 0x45, if (on) 1 else 0)
    fun big(on: Boolean) = raw(0x1D, 0x21, if (on) 0x11 else 0x00)
    fun tall(on: Boolean) = raw(0x1D, 0x21, if (on) 0x01 else 0x00)

    fun text(s: String) = apply { out.write(ascii(s).toByteArray(Charsets.US_ASCII)) }
    fun line(s: String = "") = text(s + "\n")
    fun sep(c: Char = '-') = line(c.toString().repeat(cols))

    /** Texto à esquerda e valor à direita na mesma linha. */
    fun pair(l: String, r: String, width: Int = cols) = apply {
        val left = ascii(l); val right = ascii(r)
        if (left.length + right.length + 1 > width) {
            line(left); line(right.padStart(width))
        } else line(left + " ".repeat(width - left.length - right.length) + right)
    }

    fun wrap(s: String, indent: String = "", width: Int = cols) = apply {
        var current = indent
        for (word in ascii(s).split(" ")) {
            if (current.length + word.length + 1 > width && current.isNotBlank()) {
                line(current.trimEnd()); current = indent
            }
            current += "$word "
        }
        if (current.isNotBlank()) line(current.trimEnd())
    }

    fun cut() = apply { line(); line(); line(); raw(0x1D, 0x56, 0x42, 0x00) }
    fun beep() = raw(0x1B, 0x42, 3, 2)

    fun bytes(): ByteArray = out.toByteArray()

    companion object {
        /** Remove acentos: garante impressão legível em qualquer tabela de caracteres. */
        fun ascii(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
                .map { if (it.code in 32..126 || it == '\n') it else '?' }.joinToString("")
    }
}

object Tickets {
    private fun now() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())

    /** Pedido da cozinha: só nome, quantidade e observações em destaque, sem preço. */
    fun kitchen(order: Order, items: List<OrderItem>, cols: Int, reprint: Boolean): ByteArray {
        val p = EscPos(cols)
        p.center().bold(true).big(true).line("COZINHA").big(false)
        if (reprint) p.line("** REIMPRESSAO **")
        p.big(true).line(order.title).big(false).bold(false)
        if (order.customer.isNotBlank()) p.line("Cliente: ${order.customer}")
        p.line(now()).left().sep('=')
        for (it in items) {
            p.bold(true).tall(true).wrap("${it.qty}x ${it.name.uppercase()}").tall(false).bold(false)
            if (it.obs.isNotBlank()) {
                for (o in it.obs.split(" | ")) p.bold(true).wrap(">> ${o.uppercase()}", "   ").bold(false)
            }
            p.sep()
        }
        p.line("Itens: ${items.sumOf { it.qty }}")
        return p.beep().cut().bytes()
    }

    /** Espelho/conferência da conta com preços e total. */
    fun bill(order: Order, settings: Settings, cols: Int, service: Long, discount: Long, payment: String? = null): ByteArray {
        val p = EscPos(cols)
        p.center().bold(true).big(true).line(settings.restaurant).big(false).bold(false)
        p.line(if (payment == null) "CONFERENCIA DE CONTA" else "COMPROVANTE DE PAGAMENTO")
        p.line("Nao e documento fiscal")
        p.sep('=').left()
        p.bold(true).line(order.title).bold(false)
        if (order.customer.isNotBlank()) p.line("Cliente: ${order.customer}")
        p.line(now()).sep()
        order.items.forEachIndexed { i, it ->
            p.wrap("${i + 1}) ${it.name.uppercase()}")
            p.pair("   ${it.qty} x ${money(it.price)}", money(it.total))
            if (it.obs.isNotBlank()) p.wrap("Obs: ${it.obs}", "   ")
        }
        p.sep()
        p.pair("Qtde de itens:", order.items.sumOf { it.qty }.toString())
        p.pair("Subtotal:", money(order.subtotal))
        if (service > 0) p.pair("Servico (10%):", money(service))
        if (discount > 0) p.pair("Desconto:", "-" + money(discount))
        val total = order.subtotal + service - discount
        p.bold(true).tall(true).pair("TOTAL:", money(total)).tall(false).bold(false)
        p.sep()
        if (order.people > 1) p.pair("Integrantes: ${order.people}", money(total / order.people) + " p/ cada")
        if (payment != null) p.pair("Pagamento:", payment)
        p.center().line().line("Obrigado pela preferencia!")
        return p.cut().bytes()
    }

    fun test(printer: Printer): ByteArray {
        val p = EscPos(printer.columns)
        p.center().bold(true).big(true).line("TESTE OK").big(false).bold(false)
        p.line(printer.name).line("${printer.host}:${printer.port}").line(now()).left().sep()
        p.line("0123456789".repeat(printer.columns / 10 + 1).take(printer.columns))
        p.pair("Esquerda", "Direita")
        return p.cut().bytes()
    }
}

object NetPrinter {
    /** Envia bytes RAW para a impressora (porta 9100). Retorna null em sucesso ou a mensagem de erro. */
    suspend fun send(host: String, port: Int, data: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 4000)
                s.soTimeout = 8000
                s.getOutputStream().apply { write(data); flush() }
            }
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    fun localIp(): String? = try {
        val all = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.map { nif.name to it.hostAddress } }
        (all.firstOrNull { it.first.startsWith("wlan") } ?: all.firstOrNull())?.second
    } catch (e: Exception) { null }

    private fun reachable(host: String, port: Int, timeout: Int) = try {
        Socket().use { it.connect(InetSocketAddress(host, port), timeout) }; true
    } catch (e: Exception) { false }

    /** Varre a sub-rede /24 do Wi-Fi procurando dispositivos com a porta de impressão aberta. */
    suspend fun scan(port: Int = 9100, onProgress: (Int) -> Unit, onFound: (String) -> Unit): List<String> =
        withContext(Dispatchers.IO) {
            val ip = localIp() ?: return@withContext emptyList()
            val prefix = ip.substringBeforeLast('.')
            val gate = Semaphore(48)
            var done = 0
            coroutineScope {
                (1..254).map { i ->
                    async {
                        gate.withPermit {
                            val host = "$prefix.$i"
                            val ok = host != ip && reachable(host, port, 400)
                            synchronized(this@NetPrinter) { done++ }
                            withContext(Dispatchers.Main) {
                                onProgress(done)
                                if (ok) onFound(host)
                            }
                            if (ok) host else null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
}
