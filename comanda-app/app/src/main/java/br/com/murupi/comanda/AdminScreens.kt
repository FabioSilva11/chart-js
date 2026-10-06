@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package br.com.murupi.comanda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun fmtDate(t: Long) = if (t == 0L) "-" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(t))

// =====================================================================
// Cardápio: editar preços, nomes, categorias, acompanhamentos
// =====================================================================
@Composable
fun CatalogScreen(store: Store) {
    var editing by remember { mutableStateOf<Product?>(null) }
    var onlyNoPrice by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val noPrice = store.products.count { it.price == null }

    Column(Modifier.fillMaxSize()) {
        BlueTopBar("Cardápio") {
            TextButton(onClick = { confirmReset = true }) { Text("Restaurar", color = Color.White) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${store.products.size} produtos • $noPrice sem preço", Modifier.weight(1f), fontSize = 13.sp)
            FilterChip(selected = onlyNoPrice, onClick = { onlyNoPrice = !onlyNoPrice }, label = { Text("Só sem preço") })
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                store.categories.forEach { cat ->
                    val list = store.products.filter { it.category == cat && (!onlyNoPrice || it.price == null) }
                    if (list.isNotEmpty()) {
                        item(key = "h-$cat") {
                            Text(cat, Modifier.fillMaxWidth().background(Color(0xFFE3EBF8)).padding(12.dp, 8.dp),
                                fontWeight = FontWeight.Black, color = Blue)
                        }
                        items(list, key = { it.id }) { p ->
                            Row(Modifier.fillMaxWidth().clickable { editing = p }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.SemiBold)
                                    if (p.sides.isNotEmpty()) Text(p.sides.joinToString(", "), fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(p.price?.let { money(it) } ?: "definir preço",
                                    color = if (p.price == null) Color.Red else Color.Black, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.Edit, null, tint = Color.Gray, modifier = Modifier.padding(start = 8.dp))
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            FloatingActionButton(onClick = { editing = Product(name = "", category = "", price = null) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = Blue) {
                Icon(Icons.Default.Add, "Novo produto", tint = Color.White)
            }
        }
    }

    editing?.let { p -> ProductDialog(store, p, onDismiss = { editing = null }) }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Restaurar cardápio original?") },
        text = { Text("Preços e produtos editados serão substituídos pelo cardápio original.") },
        confirmButton = { TextButton(onClick = { store.resetCatalog(); confirmReset = false }) { Text("Restaurar") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } },
    )
}

@Composable
fun ProductDialog(store: Store, product: Product, onDismiss: () -> Unit) {
    val isNew = store.products.none { it.id == product.id }
    var name by remember { mutableStateOf(product.name) }
    var category by remember { mutableStateOf(product.category) }
    var price by remember { mutableStateOf(centsToInput(product.price)) }
    var sides by remember { mutableStateOf(product.sides.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Novo produto" else "Editar produto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Preço (vazio = perguntar na hora)") }, prefix = { Text("R$ ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(category, { category = it.uppercase() }, label = { Text("Categoria") }, singleLine = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    store.categories.forEach { c ->
                        FilterChip(selected = c == category, onClick = { category = c }, label = { Text(c, fontSize = 11.sp) })
                    }
                }
                OutlinedTextField(sides, { sides = it }, label = { Text("Acompanhamentos/sabores (separados por vírgula)") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && category.isNotBlank(), onClick = {
                store.upsertProduct(product.copy(
                    name = name.trim(), category = category.trim(), price = parseMoney(price),
                    sides = sides.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                ))
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { store.deleteProduct(product.id); onDismiss() }) { Text("Excluir", color = Color.Red) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

// =====================================================================
// Impressoras térmicas na rede Wi-Fi
// =====================================================================
@Composable
fun PrintersScreen(store: Store) {
    val toast = rememberToast()
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    val found = remember { mutableStateListOf<String>() }
    var editing by remember { mutableStateOf<Printer?>(null) }
    var scanPort by remember { mutableStateOf("9100") }
    val localIp = remember { NetPrinter.localIp() }

    fun scan() {
        found.clear(); progress = 0; scanning = true
        AppScope.launch {
            val list = NetPrinter.scan(scanPort.toIntOrNull() ?: 9100, onProgress = { progress = it }, onFound = { if (it !in found) found.add(it) })
            scanning = false
            toast(if (list.isEmpty()) "Nenhuma impressora encontrada" else "${list.size} dispositivo(s) encontrado(s)")
        }
    }

    Column(Modifier.fillMaxSize()) {
        BlueTopBar("Impressoras")
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EBF8))) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, null, tint = Blue)
                            Spacer(Modifier.width(8.dp))
                            Text(if (localIp == null) "Sem Wi-Fi conectado" else "Este celular: $localIp", fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(scanPort, { scanPort = it.filter(Char::isDigit) }, label = { Text("Porta") }, singleLine = true,
                                modifier = Modifier.width(110.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            Spacer(Modifier.width(10.dp))
                            Button(onClick = { scan() }, enabled = !scanning) { Text(if (scanning) "Procurando..." else "Procurar na rede") }
                        }
                        if (scanning) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { progress / 254f }, modifier = Modifier.fillMaxWidth())
                        }
                        found.forEach { host ->
                            val saved = store.printers.any { it.host == host }
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Print, null)
                                Text(" $host", Modifier.weight(1f))
                                if (saved) Text("salva", color = Color.Gray)
                                else TextButton(onClick = {
                                    editing = Printer(name = "Impressora ${store.printers.size + 1}", host = host,
                                        port = scanPort.toIntOrNull() ?: 9100)
                                }) { Text("Adicionar") }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("IMPRESSORAS SALVAS") }
            if (store.printers.isEmpty()) item { Text("Nenhuma. Procure na rede ou adicione manualmente pelo IP.", color = Color.Gray) }
            items(store.printers, key = { it.id }) { p ->
                Card(elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${p.host}:${p.port}  •  ${p.role.label}  •  ${if (p.columns >= 48) "80mm" else "58mm"}")
                        Text("Último uso: ${fmtDate(p.lastUsed)}  ${p.lastStatus}", fontSize = 12.sp,
                            color = if (p.lastStatus.isEmpty() || p.lastStatus == "OK") Color.Gray else Color.Red)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                AppScope.launch { toast(if (printTo(store, p, Tickets.test(p), "Teste")) "Teste impresso" else "Falha: ${store.printers.firstOrNull { it.id == p.id }?.lastStatus}") }
                            }) { Text("Testar") }
                            IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, "Editar") }
                            IconButton(onClick = { store.deletePrinter(p.id) }) { Icon(Icons.Default.Delete, "Excluir") }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("HISTÓRICO DE IMPRESSÕES") }
                    TextButton(onClick = { store.clearPrintLog() }) { Text("Limpar") }
                }
            }
            items(store.printLog.take(100)) { l ->
                Row(Modifier.fillMaxWidth()) {
                    Text(if (l.ok) "✓" else "✗", color = if (l.ok) Color(0xFF27AE60) else Color.Red, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(20.dp))
                    Column {
                        Text("${fmtDate(l.at)}  ${l.what}", fontSize = 13.sp)
                        Text("${l.printer} — ${l.message}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            FloatingActionButton(onClick = { editing = Printer(name = "Impressora ${store.printers.size + 1}", host = localIp?.substringBeforeLast('.')?.plus(".") ?: "") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), containerColor = Blue) {
                Icon(Icons.Default.Add, "Adicionar", tint = Color.White)
            }
        }
    }

    editing?.let { p -> PrinterDialog(store, p) { editing = null } }
}

@Composable
fun PrinterDialog(store: Store, printer: Printer, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(printer.name) }
    var host by remember { mutableStateOf(printer.host) }
    var port by remember { mutableStateOf(printer.port.toString()) }
    var role by remember { mutableStateOf(printer.role) }
    var cols by remember { mutableStateOf(printer.columns) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impressora") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(host, { host = it.trim() }, label = { Text("IP") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Porta") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Usar para", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrinterRole.entries.forEach { r -> FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.label) }) }
                }
                Text("Papel", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = cols >= 48, onClick = { cols = 48 }, label = { Text("80mm") })
                    FilterChip(selected = cols < 48, onClick = { cols = 32 }, label = { Text("58mm") })
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && host.count { it == '.' } == 3, onClick = {
                store.upsertPrinter(printer.copy(name = name.trim(), host = host, port = port.toIntOrNull() ?: 9100, role = role, columns = cols))
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// =====================================================================
// Histórico de pedidos finalizados
// =====================================================================
@Composable
fun HistoryScreen(store: Store) {
    val toast = rememberToast()
    var detail by remember { mutableStateOf<ClosedOrder?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val startOfDay = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val today = store.closed.filter { it.closedAt >= startOfDay }

    Column(Modifier.fillMaxSize()) {
        BlueTopBar("Histórico") { TextButton(onClick = { confirmClear = true }) { Text("Limpar", color = Color.White) } }
        Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFDFF3E3))) {
            Column(Modifier.padding(12.dp)) {
                Text("Hoje: ${today.size} pedidos", fontWeight = FontWeight.SemiBold)
                Text(money(today.sumOf { it.total }), fontSize = 26.sp, fontWeight = FontWeight.Black)
                today.groupBy { it.payment }.forEach { (pay, l) -> Line(pay, money(l.sumOf { it.total })) }
            }
        }
        LazyColumn {
            items(store.closed) { c ->
                Row(Modifier.fillMaxWidth().clickable { detail = c }.padding(16.dp, 10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${c.order.title} ${c.order.customer}", fontWeight = FontWeight.SemiBold)
                        Text("${fmtDate(c.closedAt)} • ${c.payment} • ${c.order.items.sumOf { it.qty }} itens", fontSize = 12.sp, color = Color.Gray)
                    }
                    Text(money(c.total), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
            }
        }
    }

    detail?.let { c ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(c.order.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    c.order.items.forEach {
                        Line("${it.qty}x ${it.name}", money(it.total))
                        if (it.obs.isNotBlank()) Text("   ${it.obs}", fontSize = 12.sp, color = Color.Gray)
                    }
                    HorizontalDivider()
                    if (c.service > 0) Line("Serviço", money(c.service))
                    if (c.discount > 0) Line("Desconto", "-" + money(c.discount))
                    Line("TOTAL", money(c.total))
                    Line("Pagamento", c.payment)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val printer = store.printerFor(kitchen = false)
                    if (printer == null) toast("Cadastre uma impressora")
                    else AppScope.launch {
                        val ok = printTo(store, printer, Tickets.bill(c.order, store.settings, printer.columns, c.service, c.discount, c.payment), "Reimpressão ${c.order.title}")
                        toast(if (ok) "Reimpresso" else "Falha ao imprimir")
                    }
                }) { Text("Reimprimir") }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text("Fechar") } },
        )
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Apagar todo o histórico de vendas?") },
        confirmButton = { TextButton(onClick = { store.clearHistory(); confirmClear = false }) { Text("Apagar", color = Color.Red) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
    )
}

// =====================================================================
// Ajustes
// =====================================================================
@Composable
fun SettingsScreen(store: Store) {
    val toast = rememberToast()
    val s = store.settings
    var name by remember { mutableStateOf(s.restaurant) }
    var tables by remember { mutableStateOf(s.tables.toString()) }
    var fee by remember { mutableStateOf(s.serviceFee) }
    Column(Modifier.fillMaxSize()) {
        BlueTopBar("Ajustes")
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nome no cupom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tables, { tables = it.filter(Char::isDigit) }, label = { Text("Quantidade de mesas") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cobrar 10% de serviço por padrão", Modifier.weight(1f)); Switch(fee, { fee = it })
            }
            Button(onClick = {
                store.updateSettings(s.copy(restaurant = name.trim(), tables = (tables.toIntOrNull() ?: 60).coerceIn(1, 300), serviceFee = fee))
                toast("Salvo")
            }, modifier = Modifier.fillMaxWidth()) { Text("Salvar") }
            Text("Cores das mesas: verde = livre, azul = ocupada, rosa = conta impressa.", color = Color.Gray, fontSize = 13.sp)
        }
    }
}
