@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.murupi.comanda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(store: Store, nav: Nav) {
    var tab by rememberSaveable { mutableStateOf(0) }
    // Mesa/comanda selecionada: mostra o painel com as subdivisões (9.1, 9.2 ...)
    var selected by remember { mutableStateOf<Pair<OrderType, Int>?>(null) }
    var askName by remember { mutableStateOf<Pair<OrderType, Int>?>(null) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF2F4F7))) {
        // Abas superiores, como no app original
        Row(Modifier.fillMaxWidth().background(Blue).padding(top = 8.dp, bottom = 6.dp)) {
            listOf(
                Triple("Mesa", Icons.Default.TableRestaurant, 0),
                Triple("Balcão", Icons.Default.Storefront, 1),
                Triple("Comanda", Icons.Default.ConfirmationNumber, 2),
            ).forEach { (label, icon, i) -> TopTab(label, icon, tab == i, Modifier.weight(1f)) { tab = i; selected = null } }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> TablesGrid(store, OrderType.MESA, (1..store.settings.tables).toList()) { selected = OrderType.MESA to it }
                1 -> BalcaoList(store, nav) { askName = OrderType.BALCAO to 0 }
                else -> ComandaTab(store) { selected = OrderType.COMANDA to it }
            }

            selected?.let { (type, number) ->
                SubPanel(
                    store, type, number,
                    modifier = Modifier.align(Alignment.Center),
                    onClose = { selected = null },
                    onOpen = { selected = null; nav.push(Screen.OrderView(it)) },
                    onNew = { askName = type to number },
                )
            }
        }
    }

    askName?.let { (type, number) ->
        NewOrderDialog(
            title = if (type == OrderType.BALCAO) "Novo pedido balcão" else "Nova ${type.label.lowercase()} $number",
            onDismiss = { askName = null },
        ) { name ->
            val o = if (type == OrderType.BALCAO) store.newBalcao(name) else store.openOrder(type, number, name)
            askName = null; selected = null
            nav.push(Screen.OrderView(o.id))
        }
    }
}

@Composable
private fun TopTab(label: String, icon: ImageVector, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = if (active) Color.White else Color.White.copy(alpha = 0.55f)
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c, modifier = Modifier.size(30.dp))
        Text(label, color = c, fontSize = 12.sp)
    }
}

fun statusColor(orders: List<Order>): Color = when {
    orders.isEmpty() -> FreeGreen
    orders.all { it.billPrinted } -> BillPink
    else -> BusyBlue
}

@Composable
fun TablesGrid(store: Store, type: OrderType, numbers: List<Int>, onTap: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(numbers) { n ->
            val orders = store.ordersOf(type, n)
            Box(
                Modifier.aspectRatio(1f).background(statusColor(orders), CircleShape).clickable { onTap(n) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$n", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF111111))
                    // "mesa" estilizada: tampo + pé
                    Box(Modifier.width(44.dp).height(5.dp).background(Color(0xFF555B66), RoundedCornerShape(3.dp)))
                    Box(Modifier.width(4.dp).height(12.dp).background(Color(0xFF555B66)))
                    if (orders.isNotEmpty()) Text(money(orders.sumOf { it.subtotal }), fontSize = 9.sp, color = Color(0xFF333333))
                }
            }
        }
    }
}

@Composable
fun SubPanel(
    store: Store, type: OrderType, number: Int, modifier: Modifier,
    onClose: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit,
) {
    val subs = store.ordersOf(type, number)
    Card(
        modifier.fillMaxWidth().padding(horizontal = 4.dp),
        shape = RoundedCornerShape(4.dp),
        elevation = CardDefaults.cardElevation(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF4A90E2)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fechar", tint = Color.White) }
            Text(
                if (type == OrderType.MESA) "Mesas" else "${type.label}s",
                color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = onNew) { Icon(Icons.Default.Add, "Nova", tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
        if (subs.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${type.label} $number livre")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onNew) { Text("Abrir ${type.label.lowercase()} $number.1") }
            }
        } else {
            LazyRow(contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(subs) { o ->
                    Column(
                        Modifier.size(110.dp, 90.dp)
                            .background(if (o.billPrinted) BillPink else Color(0xFFD6E8FA), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(6.dp))
                            .clickable { onOpen(o.id) }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(o.code, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(money(o.subtotal), fontSize = 12.sp)
                        if (o.customer.isNotBlank()) Text(o.customer, fontStyle = FontStyle.Italic, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun BalcaoList(store: Store, nav: Nav, onNew: () -> Unit) {
    val list = store.orders.filter { it.type == OrderType.BALCAO }.sortedBy { it.createdAt }
    Box(Modifier.fillMaxSize()) {
        if (list.isEmpty()) Text("Nenhum pedido no balcão", Modifier.align(Alignment.Center), color = Color.Gray)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 90.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list) { o -> OrderTile(o) { nav.push(Screen.OrderView(o.id)) } }
        }
        ExtendedFloatingActionButton(
            onClick = onNew, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Default.Add, null) }, text = { Text("Novo pedido") },
        )
    }
}

@Composable
fun OrderTile(o: Order, onClick: () -> Unit) {
    Column(
        Modifier.height(90.dp).background(if (o.billPrinted) BillPink else BusyBlue, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("#${o.number}", fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(money(o.subtotal), fontSize = 12.sp)
        if (o.customer.isNotBlank()) Text(o.customer, fontStyle = FontStyle.Italic, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
fun ComandaTab(store: Store, onSelect: (Int) -> Unit) {
    var number by remember { mutableStateOf("") }
    val open = store.orders.filter { it.type == OrderType.COMANDA }.map { it.number }.distinct().sorted()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = number, onValueChange = { number = it.filter(Char::isDigit).take(5) },
                label = { Text("Número da comanda") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { number.toIntOrNull()?.let { onSelect(it); number = "" } }) { Text("Abrir") }
        }
        if (open.isEmpty()) Text("Nenhuma comanda aberta", Modifier.padding(16.dp), color = Color.Gray)
        else TablesGrid(store, OrderType.COMANDA, open, onSelect)
    }
}

@Composable
fun NewOrderDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it }, label = { Text("Nome do cliente (opcional)") }, singleLine = true)
        },
        confirmButton = { Button(onClick = { onConfirm(name.trim()) }) { Text("Abrir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
