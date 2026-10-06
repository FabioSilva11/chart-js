@file:OptIn(ExperimentalMaterial3Api::class)

package br.com.murupi.comanda

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

val Blue = Color(0xFF2F6FDB)
val BlueDark = Color(0xFF1E5BC6)
val FreeGreen = Color(0xFF8FD694)
val BusyBlue = Color(0xFFA9CBEE)
val BillPink = Color(0xFFF6CFD6)

sealed class Screen {
    object Home : Screen()
    data class OrderView(val orderId: String) : Screen()
    data class AddProduct(val orderId: String) : Screen()
    object CatalogView : Screen()
    object Printers : Screen()
    object History : Screen()
    object SettingsView : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = BlueDark)) {
                App(store)
            }
        }
    }
}

/** Navegação simples por pilha. */
class Nav(start: Screen) {
    var stack by mutableStateOf(listOf(start)); private set
    val current get() = stack.last()
    fun push(s: Screen) { stack = stack + s }
    fun pop(): Boolean = if (stack.size > 1) { stack = stack.dropLast(1); true } else false
    fun root(s: Screen) { stack = listOf(s) }
}

@Composable
fun App(store: Store) {
    val nav = remember { Nav(Screen.Home) }
    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    when (val s = nav.current) {
        Screen.Home, Screen.CatalogView, Screen.Printers, Screen.History, Screen.SettingsView ->
            MainTabs(store, nav, s)
        is Screen.OrderView -> {
            if (store.order(s.orderId) == null) LaunchedEffect(s) { nav.pop() }
            else OrderScreen(store, nav, s.orderId)
        }
        is Screen.AddProduct -> AddProductScreen(store, nav, s.orderId)
    }
}

@Composable
fun MainTabs(store: Store, nav: Nav, s: Screen) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple(Screen.Home, "Mesas", Icons.Default.TableRestaurant),
                    Triple(Screen.CatalogView, "Cardápio", Icons.Default.MenuBook),
                    Triple(Screen.Printers, "Impressoras", Icons.Default.Print),
                    Triple(Screen.History, "Histórico", Icons.Default.History),
                    Triple(Screen.SettingsView, "Ajustes", Icons.Default.Settings),
                ).forEach { (screen, label, icon) ->
                    NavigationBarItem(
                        selected = s == screen,
                        onClick = { nav.root(screen) },
                        icon = { Icon(icon, null) },
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (s) {
                Screen.CatalogView -> CatalogScreen(store)
                Screen.Printers -> PrintersScreen(store)
                Screen.History -> HistoryScreen(store)
                Screen.SettingsView -> SettingsScreen(store)
                else -> HomeScreen(store, nav)
            }
        }
    }
}

@Composable
fun BlueTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
        },
        actions = { Row { actions() } },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Blue, titleContentColor = Color.White,
            navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
        ),
    )
}

/** Imprime e registra no histórico de impressões. */
suspend fun printTo(store: Store, printer: Printer, data: ByteArray, what: String): Boolean {
    val err = NetPrinter.send(printer.host, printer.port, data)
    store.logPrint(printer, what, err == null, err ?: "Impresso")
    return err == null
}

@Composable
fun rememberToast(): (String) -> Unit {
    val ctx = LocalContext.current
    return remember { { msg: String -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() } }
}
