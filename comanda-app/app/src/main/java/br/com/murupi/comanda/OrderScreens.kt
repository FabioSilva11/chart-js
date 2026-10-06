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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private val TextBlue = Color(0xFF2A5CB8)

// =====================================================================
// Tela do pedido (Mesa 9.1)
// =====================================================================
@Composable
fun OrderScreen(store: Store, nav: Nav, orderId: String) {
    val order = store.order(orderId) ?: return
    val toast = rememberToast()
    var menu by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<OrderItem?>(null) }
    var transferItem by remember { mutableStateOf<OrderItem?>(null) }
    var editCustomer by remember { mutableStateOf(false) }
    var editPeople by remember { mutableStateOf(false) }
    var finalize by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    fun kitchen(all: Boolean) {
        val items = if (all) order.items else order.items.filterNot { it.sent }
        if (items.isEmpty()) { toast(if (all) "Pedido vazio" else "Nenhum item novo para a cozinha"); return }
        val printer = store.printerFor(kitchen = true) ?: run { toast("Cadastre uma impressora em Impressoras"); return }
        AppScope.launch {
            val ok = printTo(store, printer, Tickets.kitchen(order, items, printer.columns, reprint = all), "Cozinha ${order.title}")
            if (ok) { store.markSent(order.id, items.map { it.id }.toSet()); toast("Enviado para a cozinha") }
            else toast("Falha ao imprimir na ${printer.name}")
        }
    }

    fun bill() {
        if (order.items.isEmpty()) { toast("Pedido vazio"); return }
        val printer = store.printerFor(kitchen = false) ?: run { toast("Cadastre uma impressora em Impressoras"); return }
        val service = if (store.settings.serviceFee) order.subtotal / 10 else 0L
        AppScope.launch {
            val ok = printTo(store, printer, Tickets.bill(order, store.settings, printer.columns, service, 0), "Espelho ${order.title}")
            if (ok) { store.order(order.id)?.let { store.updateOrder(it.copy(billPrinted = true)) }; toast("Espelho impresso") }
            else toast("Falha ao imprimir na ${printer.name}")
        }
    }

    Scaffold(
        topBar = {
            Column {
                BlueTopBar(order.title + if (order.customer.isNotBlank()) " - ${order.customer}" else "", onBack = { nav.pop() }) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Enviar para cozinha") }, onClick = { menu = false; kitchen(false) })
                        DropdownMenuItem(text = { Text("Reimprimir cozinha (tudo)") }, onClick = { menu = false; kitchen(true) })
                        DropdownMenuItem(text = { Text("Imprimir espelho / conta") }, onClick = { menu = false; bill() })
                        DropdownMenuItem(text = { Text("Finalizar pedido") }, onClick = { menu = false; finalize = true })
                        DropdownMenuItem(text = { Text("Cancelar pedido", color = Color.Red) }, onClick = { menu = false; confirmCancel = true })
                    }
                }
                Row(Modifier.fillMaxWidth().background(Blue).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clickable { editCustomer = true }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text(order.customer.ifBlank { "Cliente" }, color = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.clickable { editPeople = true }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("${order.people}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.push(Screen.AddProduct(order.id)) }, shape = CircleShape, containerColor = Blue) {
                Icon(Icons.Default.Add, "Incluir produto", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Total", fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Text(money(order.subtotal), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val pending = order.items.count { !it.sent }
                        Button(onClick = { kitchen(false) }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE67E22))) {
                            Text(if (pending > 0) "Cozinha ($pending)" else "Cozinha")
                        }
                        Button(onClick = { bill() }, modifier = Modifier.weight(1f)) { Text("Espelho") }
                        Button(onClick = { finalize = true }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60))) { Text("Finalizar") }
                    }
                }
            }
        },
    ) { pad ->
        if (order.items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Inventory2, null, tint = Color.Gray, modifier = Modifier.size(90.dp))
                Text("Nenhum produto encontrado", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(order.items, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth().clickable { editItem = item }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name.uppercase(), color = TextBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Row {
                                Text("${item.qty} x ${money(item.price)}", fontStyle = FontStyle.Italic, modifier = Modifier.width(130.dp))
                                Text(money(item.total), fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold)
                            }
                            if (item.obs.isNotBlank()) Text(item.obs, fontSize = 13.sp, color = Color(0xFFC0392B))
                            Text(if (item.sent) "✓ enviado à cozinha" else "• pendente", fontSize = 11.sp,
                                color = if (item.sent) Color(0xFF27AE60) else Color(0xFFE67E22))
                        }
                        IconButton(onClick = { transferItem = item }) { Icon(Icons.Default.SwapHoriz, "Transferir", tint = TextBlue) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    editItem?.let { item ->
        val product = store.products.firstOrNull { it.id == item.productId }
            ?: Product(id = item.productId, name = item.name, category = "", price = item.price)
        ItemEditor(store, product, item, onDismiss = { editItem = null },
            onDelete = { store.removeItem(order.id, item.id); editItem = null }) {
            store.updateItem(order.id, it.copy(sent = it.sent && it.obs == item.obs && it.qty == item.qty))
            editItem = null
        }
    }

    transferItem?.let { item ->
        TransferDialog(store, order, onDismiss = { transferItem = null }) { targetId ->
            store.transferItem(order.id, item.id, targetId); transferItem = null; toast("Item transferido")
        }
    }

    if (editCustomer) TextInputDialog("Nome do cliente", order.customer, onDismiss = { editCustomer = false }) {
        store.updateOrder(order.copy(customer = it)); editCustomer = false
    }
    if (editPeople) TextInputDialog("Integrantes (dividir conta)", order.people.toString(), KeyboardType.Number, { editPeople = false }) {
        store.updateOrder(order.copy(people = (it.toIntOrNull() ?: 1).coerceAtLeast(1))); editPeople = false
    }
    if (confirmCancel) AlertDialog(
        onDismissRequest = { confirmCancel = false },
        title = { Text("Cancelar ${order.title}?") },
        text = { Text("Todos os itens serão apagados.") },
        confirmButton = { TextButton(onClick = { confirmCancel = false; store.removeOrder(order.id); nav.pop() }) { Text("Cancelar pedido", color = Color.Red) } },
        dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Voltar") } },
    )
    if (finalize) FinalizeDialog(store, order, onDismiss = { finalize = false }) { nav.pop() }
}

// =====================================================================
// Incluir produto: categorias coloridas + busca
// =====================================================================
private val CategoryColors = listOf(
    Color(0xFF3DB8E8), Color(0xFF9EA7B3), Color(0xFF1FA645), Color(0xFFDD5347),
    Color(0xFFE8B321), Color(0xFF2F6FDB), Color(0xFF1E1A26),
)

@Composable
fun AddProductScreen(store: Store, nav: Nav, orderId: String) {
    val toast = rememberToast()
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<Product?>(null) }

    androidx.activity.compose.BackHandler(enabled = category != null) { category = null }

    Scaffold(topBar = {
        BlueTopBar(category ?: "Incluir Produto", onBack = { if (category != null) category = null else nav.pop() })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Cód/Nome Produto") },
                trailingIcon = { Icon(Icons.Default.Search, null, tint = Blue) },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
            val list = when {
                query.isNotBlank() -> store.products.filter {
                    EscPos.ascii(it.name).contains(EscPos.ascii(query), ignoreCase = true) || it.id == query
                }
                category != null -> store.products.filter { it.category == category }
                else -> null
            }
            if (list == null) {
                LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                    items(store.categories.withIndex().toList()) { (i, cat) ->
                        Box(
                            Modifier.height(92.dp).padding(1.dp).background(CategoryColors[i % CategoryColors.size])
                                .clickable { category = cat },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(cat, color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                                fontSize = 14.sp, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            } else {
                LazyColumn {
                    items(list, key = { it.id }) { p ->
                        Row(Modifier.fillMaxWidth().clickable { picking = p }.padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name.uppercase(), fontWeight = FontWeight.Bold, color = TextBlue)
                                if (p.sides.isNotEmpty() && !Catalog.sidesAreFlavors(p))
                                    Text(p.sides.joinToString(", "), fontSize = 12.sp, color = Color.Gray)
                            }
                            Text(p.price?.let { money(it) } ?: "sem preço",
                                color = if (p.price == null) Color.Red else Color.Black, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    picking?.let { p ->
        ItemEditor(store, p, null, onDismiss = { picking = null }) { item ->
            store.addItem(orderId, item)
            picking = null
            toast("${item.qty}x ${item.name} adicionado")
        }
    }
}

// =====================================================================
// Item: quantidade, preço e OBSERVAÇÕES (retirar acompanhamentos, sem cebola...)
// =====================================================================
@Composable
fun ItemEditor(
    store: Store, product: Product, existing: OrderItem?,
    onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (OrderItem) -> Unit,
) {
    val flavors = Catalog.sidesAreFlavors(product)
    // Reconstrói o estado a partir da observação já salva (edição).
    val parts = remember { existing?.obs?.split(" | ")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList() }
    var qty by remember { mutableStateOf(existing?.qty ?: 1) }
    var priceText by remember { mutableStateOf(centsToInput(existing?.price ?: product.price)) }
    var savePrice by remember { mutableStateOf(product.price == null) }
    val removed = remember { mutableStateListOf<String>().apply { addAll(product.sides.filter { "Sem ${it.lowercase()}" in parts.map(String::lowercase) }) } }
    var flavor by remember { mutableStateOf(product.sides.firstOrNull { it in parts }) }
    val quick = remember { mutableStateListOf<String>().apply { addAll(QUICK_OBS.filter { it in parts }) } }
    var free by remember {
        mutableStateOf(parts.filterNot { p ->
            p in QUICK_OBS || p == flavor || product.sides.any { "Sem ${it.lowercase()}".equals(p, true) }
        }.joinToString(", "))
    }

    fun buildObs(): String = buildList {
        if (flavors) flavor?.let { add(it) } else removed.forEach { add("Sem ${it.lowercase()}") }
        addAll(quick)
        if (free.isNotBlank()) add(free.trim())
    }.joinToString(" | ")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(Modifier.fillMaxSize().imePadding()) {
                BlueTopBar(product.name, onBack = onDismiss) {
                    if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Excluir") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    // Quantidade
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quantidade", fontSize = 18.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (qty > 1) qty-- }) { Icon(Icons.Default.Remove, "Menos") }
                        Text("$qty", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                        IconButton(onClick = { qty++ }) { Icon(Icons.Default.Add, "Mais") }
                    }
                    // Preço (obrigatório quando o produto não tem preço no cardápio)
                    OutlinedTextField(
                        value = priceText, onValueChange = { priceText = it },
                        label = { Text(if (product.price == null) "Preço unitário (sem preço no cardápio)" else "Preço unitário") },
                        prefix = { Text("R$ ") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = parseMoney(priceText) == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (product.price == null && product.category.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(savePrice, { savePrice = it })
                        Text("Salvar este preço no cardápio")
                    }

                    Spacer(Modifier.height(16.dp))
                    SectionTitle("OBSERVAÇÕES")

                    if (product.sides.isNotEmpty()) {
                        Text(if (flavors) "Sabor" else "Acompanhamentos — toque para RETIRAR",
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            product.sides.forEach { side ->
                                if (flavors) {
                                    FilterChip(selected = flavor == side, onClick = { flavor = if (flavor == side) null else side },
                                        label = { Text(side) })
                                } else {
                                    val included = side !in removed
                                    FilterChip(
                                        selected = included,
                                        onClick = { if (included) removed.add(side) else removed.remove(side) },
                                        label = { Text(if (included) side else "SEM ${side.uppercase()}") },
                                        leadingIcon = { Icon(if (included) Icons.Default.Check else Icons.Default.Close, null, Modifier.size(16.dp)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = Color(0xFFFDE2E1), labelColor = Color(0xFFC0392B),
                                            iconColor = Color(0xFFC0392B),
                                            selectedContainerColor = Color(0xFFDFF3E3),
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    Text("Rápidas", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QUICK_OBS.forEach { q ->
                            FilterChip(selected = q in quick, onClick = { if (q in quick) quick.remove(q) else quick.add(q) }, label = { Text(q) })
                        }
                    }

                    OutlinedTextField(
                        value = free, onValueChange = { free = it },
                        label = { Text("Outra observação (ex: tirar cebola, sem tempero)") },
                        minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )

                    val obs = buildObs()
                    if (obs.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Vai para a cozinha:", fontSize = 12.sp, color = Color.Gray)
                        Text(obs, color = Color(0xFFC0392B), fontWeight = FontWeight.SemiBold)
                    }
                }
                val price = parseMoney(priceText)
                Button(
                    onClick = {
                        val p = price ?: return@Button
                        if (savePrice && product.price == null && product.category.isNotEmpty()) store.upsertProduct(product.copy(price = p))
                        onSave((existing ?: OrderItem(productId = product.id, name = product.name, price = p, qty = qty))
                            .copy(price = p, qty = qty, obs = buildObs()))
                    },
                    enabled = price != null,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp),
                ) {
                    Text(if (existing == null) "Adicionar  •  ${money((price ?: 0) * qty)}" else "Salvar  •  ${money((price ?: 0) * qty)}", fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(t: String) {
    Text(t, color = Blue, fontWeight = FontWeight.Black, fontSize = 15.sp)
    HorizontalDivider(color = Blue, thickness = 2.dp)
}

// =====================================================================
// Finalizar pedido
// =====================================================================
private val PAYMENTS = listOf("Dinheiro", "Pix", "Cartão Crédito", "Cartão Débito")

@Composable
fun FinalizeDialog(store: Store, order: Order, onDismiss: () -> Unit, onDone: () -> Unit) {
    val toast = rememberToast()
    var service by remember { mutableStateOf(store.settings.serviceFee) }
    var discountText by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf(PAYMENTS[0]) }
    var receivedText by remember { mutableStateOf("") }
    var people by remember { mutableStateOf(order.people) }

    val serviceValue = if (service) order.subtotal / 10 else 0L
    val discount = parseMoney(discountText) ?: 0L
    val total = order.subtotal + serviceValue - discount
    val received = parseMoney(receivedText)

    fun finish(print: Boolean) {
        val o = order.copy(people = people)
        store.updateOrder(o)
        if (print) {
            val printer = store.printerFor(kitchen = false)
            if (printer == null) toast("Sem impressora cadastrada; pedido finalizado sem imprimir")
            else {
                val data = Tickets.bill(o, store.settings, printer.columns, serviceValue, discount, payment)
                AppScope.launch { if (!printTo(store, printer, data, "Comprovante ${o.title}")) toast("Falha ao imprimir") }
            }
        }
        store.finalize(o.id, serviceValue, discount, payment)
        toast("${o.title} finalizada: ${money(total)}")
        onDismiss(); onDone()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finalizar ${order.title}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Line("Subtotal", money(order.subtotal))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Serviço 10%", Modifier.weight(1f)); Switch(service, { service = it })
                }
                if (service) Line("Serviço", money(serviceValue))
                OutlinedTextField(discountText, { discountText = it }, label = { Text("Desconto (R$)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL", fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Text(money(total), fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Integrantes", Modifier.weight(1f))
                    IconButton(onClick = { if (people > 1) people-- }) { Icon(Icons.Default.Remove, null) }
                    Text("$people")
                    IconButton(onClick = { people++ }) { Icon(Icons.Default.Add, null) }
                }
                if (people > 1) Line("Por pessoa", money(total / people))
                Text("Pagamento", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PAYMENTS.forEach { FilterChip(selected = payment == it, onClick = { payment = it }, label = { Text(it) }) }
                }
                if (payment == "Dinheiro") {
                    OutlinedTextField(receivedText, { receivedText = it }, label = { Text("Valor recebido") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (received != null && received >= total) Line("Troco", money(received - total))
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { finish(true) }) { Text("Finalizar e imprimir") }
                OutlinedButton(onClick = { finish(false) }) { Text("Finalizar sem imprimir") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Voltar") } },
    )
}

@Composable
fun Line(l: String, r: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(l, Modifier.weight(1f)); Text(r, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun TransferDialog(store: Store, from: Order, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var tableText by remember { mutableStateOf("") }
    val others = store.orders.filter { it.id != from.id }.sortedWith(compareBy({ it.type }, { it.number }, { it.sub }))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transferir item para") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(tableText, { tableText = it.filter(Char::isDigit) }, label = { Text("Nova mesa nº") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        tableText.toIntOrNull()?.let { onPick(store.openOrder(OrderType.MESA, it).id) }
                    }) { Text("Abrir") }
                }
                LazyColumn(Modifier.height(260.dp)) {
                    items(others) { o ->
                        Text("${o.title} ${o.customer}  •  ${money(o.subtotal)}",
                            Modifier.fillMaxWidth().clickable { onPick(o.id) }.padding(12.dp))
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun TextInputDialog(
    title: String, initial: String, keyboard: KeyboardType = KeyboardType.Text,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard)) },
        confirmButton = { Button(onClick = { onConfirm(text.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
