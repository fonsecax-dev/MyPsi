package com.aodaci.mypsi

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import android.os.Bundle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/** Pedido vindo do widget: abrir o ficheiro e pôr o cursor no VALOR desta linha. [vez] distingue toques repetidos. */
data class PedidoLinha(val ficheiroId: Long, val linhaId: Long, val vez: Long = System.nanoTime())

const val EXTRA_FICHEIRO = "abrir_ficheiro"
const val EXTRA_LINHA = "abrir_linha"

class MainActivity : ComponentActivity() {
    private val pedido = mutableStateOf<PedidoLinha?>(null)
    private val externo = mutableStateOf<Uri?>(null)

    private fun lerPedido(intent: Intent?) {
        // "Abrir com MyPsi" (VIEW) ou "Partilhar → MyPsi" (SEND) de um Excel
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (uri != null) { externo.value = uri; intent?.action = null } // não reimportar se a atividade for recriada
        val f = intent?.getLongExtra(EXTRA_FICHEIRO, -1) ?: -1
        val l = intent?.getLongExtra(EXTRA_LINHA, -1) ?: -1
        if (f >= 0 && l >= 0) pedido.value = PedidoLinha(f, l)
        // não repetir o pedido se a atividade for recriada (rodar o ecrã)
        intent?.removeExtra(EXTRA_FICHEIRO); intent?.removeExtra(EXTRA_LINHA)
    }

    override fun onStart() {
        super.onStart()
        CopiaAutomatica.talvezFazer(this)
        Lembrete.agendar(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lerPedido(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        lerPedido(intent)
        setContent {
            val estado by Store.estado.collectAsState()
            val densidade = LocalDensity.current
            val escuro = when (estado.tema) {
                TEMA_CLARO -> false
                TEMA_ESCURO -> true
                else -> isSystemInDarkTheme()
            }
            // ícones da barra de estado legíveis no tema escolhido na app (não só no do sistema)
            DisposableEffect(escuro) {
                val estilo = if (escuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
                onDispose {}
            }
            MaterialTheme(colorScheme = esquema(Color(estado.corBase), escuro)) {
                CompositionLocalProvider(LocalDensity provides Density(densidade.density, densidade.fontScale * estado.escalaTexto)) {
                    App(pedido.value, { pedido.value = null }, externo.value) { externo.value = null }
                }
            }
        }
    }
}

/** Esquema de cores derivado da cor base escolhida nas Opções; fundos e cartões levemente tingidos com ela. */
internal fun esquema(c: Color, escuro: Boolean) = if (escuro) { // também usado pelo widget
    val principal = lerp(c, Color.White, 0.45f)
    val contentor = lerp(c, Color.Black, 0.55f)
    val base = Color(0xFF121212)
    fun tom(f: Float) = lerp(base, c, f) // mais cor = mais claro sobre o fundo escuro
    darkColorScheme(
        primary = principal, onPrimary = Color.Black, secondary = principal, tertiary = principal,
        primaryContainer = contentor, onPrimaryContainer = Color.White,
        secondaryContainer = contentor, onSecondaryContainer = Color.White,
        background = tom(0.04f), surface = tom(0.04f),
        surfaceContainerLowest = base, surfaceContainerLow = tom(0.07f), surfaceContainer = tom(0.10f),
        surfaceContainerHigh = tom(0.13f), surfaceContainerHighest = tom(0.16f),
        surfaceVariant = tom(0.22f),
    )
} else {
    val contentor = lerp(c, Color.White, 0.8f)
    fun tom(f: Float) = lerp(c, Color.White, f)
    lightColorScheme(
        primary = c, onPrimary = Color.White, secondary = c, tertiary = c,
        primaryContainer = contentor, onPrimaryContainer = Color.Black,
        secondaryContainer = contentor, onSecondaryContainer = Color.Black,
        background = tom(0.97f), surface = tom(0.97f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = tom(0.95f), surfaceContainer = tom(0.93f),
        surfaceContainerHigh = tom(0.91f), surfaceContainerHighest = tom(0.89f),
        surfaceVariant = tom(0.86f),
    )
}

@Composable
private fun App(pedido: PedidoLinha?, pedidoAtendido: () -> Unit, externo: Uri?, externoAtendido: () -> Unit) {
    val estado by Store.estado.collectAsState()
    var separador by rememberSaveable { mutableIntStateOf(0) }
    // ao abrir a app entra-se logo no último ficheiro, se ainda tiver linhas por tratar; senão fica a lista
    var abertoId by rememberSaveable {
        mutableStateOf(Store.estado.value.ficheiros.lastOrNull()?.takeIf { f -> f.linhas.any { !tratada(it) } }?.id)
    }
    // toque numa linha do widget: ir para Dados, abrir esse ficheiro (a página trata do cursor)
    var ajuda by rememberSaveable { mutableStateOf(false) }
    val avisos = remember { SnackbarHostState() }
    val ambito = rememberCoroutineScope()
    val oferecerDesfazer: (String) -> Unit = { texto ->
        ambito.launch {
            avisos.currentSnackbarData?.dismiss()
            if (avisos.showSnackbar(texto, actionLabel = "Desfazer", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                if (!Store.desfazer()) avisos.showSnackbar("Já não é possível desfazer.", duration = SnackbarDuration.Short)
            }
        }
    }
    LaunchedEffect(pedido) {
        if (pedido != null) { separador = 0; abertoId = pedido.ficheiroId; ajuda = false }
    }
    // Excel aberto a partir de outra app: ir para a lista de ficheiros, que pergunta a ordenação e importa
    LaunchedEffect(externo) {
        if (externo != null) { separador = 0; abertoId = null; ajuda = false }
    }
    // filtro de período partilhado por Dados e Dashboard; começa no ano do último ficheiro, ano completo
    var ano by rememberSaveable {
        mutableIntStateOf(Store.estado.value.ficheiros.lastOrNull()?.categoria?.ano ?: categoriaDaData(System.currentTimeMillis()).ano)
    }
    var mes by rememberSaveable { mutableStateOf<Int?>(null) }
    var quinzena by rememberSaveable { mutableStateOf<Int?>(null) }
    val filtro = @Composable { FiltroPeriodo(estado, ano, mes, quinzena) { a, m, q -> ano = a; mes = m; quinzena = q } }

    Scaffold(snackbarHost = { SnackbarHost(avisos) }, bottomBar = {
        NavigationBar {
            listOf(R.drawable.ic_dados to "Dados", R.drawable.ic_dashboard to "Dashboard", R.drawable.ic_opcoes to "Opções").forEachIndexed { i, (icone, titulo) ->
                NavigationBarItem(
                    selected = separador == i && !ajuda,
                    onClick = { separador = i; ajuda = false },
                    icon = { Icone(icone, null, tint = androidx.compose.material3.LocalContentColor.current) }, // o rótulo já diz o nome
                    label = { Text(titulo) },
                )
            }
        }
    }) { margens ->
        Box(Modifier.padding(margens).consumeWindowInsets(margens).imePadding().fillMaxSize()) {
            CompositionLocalProvider(LocalAbrirAjuda provides { ajuda = true }, LocalDesfazer provides oferecerDesfazer) {
                if (ajuda) AjudaScreen { ajuda = false }
                else when (separador) {
                    0 -> DadosScreen(estado, abertoId, filtro, { noPeriodo(it, ano, mes, quinzena) }, pedido, pedidoAtendido, externo, externoAtendido) { abertoId = it }
                    1 -> DashboardScreen(estado, filtro, estado.ficheiros.filter { noPeriodo(it, ano, mes, quinzena) })
                    else -> OpcoesScreen(estado)
                }
            }
        }
    }
}
