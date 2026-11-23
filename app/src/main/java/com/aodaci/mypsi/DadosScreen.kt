package com.aodaci.mypsi

import android.content.Context
import android.content.Intent
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val IMPORTAR_FICHEIRO = 0
private const val IMPORTAR_ROTA = 1
private const val IMPORTAR_HISTORICO = 2
private const val TRATADAS = "§tratadas" // chave da secção das tratadas (não colide com "g:<local>")
private val LARG_PEGA = 48.dp // área de toque mínima recomendada
private val LARG_VALOR = 80.dp
private val MIN_TEXTO = 110.dp // largura mínima reservada a PDV/PRODUTO/OBS
private val MIN_OPCAO = 48.dp // área de toque mínima do botão de seleção
private val MAX_OPCAO = 80.dp
private val VALOR_VALIDO = Regex("-?\\d{0,9}([.,]\\d{0,2})?")

@Composable
fun DadosScreen(
    estado: Estado,
    abertoId: Long?,
    filtro: @Composable () -> Unit,
    noFiltro: (Ficheiro) -> Boolean,
    pedido: PedidoLinha?,
    pedidoAtendido: () -> Unit,
    externo: Uri?,
    externoAtendido: () -> Unit,
    abrir: (Long?) -> Unit,
) {
    val ficheiro = estado.ficheiros.find { it.id == abertoId }
    if (ficheiro == null) ListaFicheiros(estado, filtro, noFiltro, externo, externoAtendido, abrir)
    else {
        BackHandler { abrir(null) }
        PaginaFicheiro(ficheiro, estado.opcoes.filter { it.visivel }, pedido?.takeIf { it.ficheiroId == ficheiro.id }, pedidoAtendido) { abrir(null) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListaFicheiros(
    estado: Estado,
    filtro: @Composable () -> Unit,
    noFiltro: (Ficheiro) -> Boolean,
    externo: Uri?,
    externoAtendido: () -> Unit,
    abrir: (Long?) -> Unit,
) {
    val contexto = LocalContext.current
    val ambito = rememberCoroutineScope()
    var escolherOrdem by remember { mutableStateOf(false) }
    var modo by rememberSaveable { mutableStateOf(IMPORTAR_FICHEIRO) }
    var ocupado by remember { mutableStateOf(false) }
    var apagar by remember { mutableStateOf<Ficheiro?>(null) }
    val desfazer = LocalDesfazer.current

    // ficheiro vindo de "Abrir com MyPsi" (email, WhatsApp...): importa-se depois de escolher a ordenação
    var pendente by remember { mutableStateOf<Uri?>(null) }
    LaunchedEffect(externo) {
        if (externo != null) { pendente = externo; escolherOrdem = true; externoAtendido() }
    }

    fun importarUri(uri: Uri) {
        ambito.launch {
            ocupado = true
            try {
                var linhas = withContext(Dispatchers.IO) { contexto.contentResolver.openInputStream(uri)!!.use(::parseXlsx) }
                require(linhas.isNotEmpty())
                if (modo == IMPORTAR_ROTA) {
                    val ordem = ordemRota(contexto, linhas, Store.estado.value.moradaPartida)
                    if (ordem == null) aviso(contexto, "Não foi possível localizar os PDV (sem internet?). Importado com a ordenação do ficheiro.")
                    else linhas = ordem.map { linhas[it] }
                } else if (modo == IMPORTAR_HISTORICO) {
                    val historico = Store.estado.value.ficheiros
                    val conhecidos = historico.flatMap { f -> f.linhas.map { "${it.local.trim().uppercase()}|${it.pdv.trim().uppercase()}" } }.toSet()
                    val comHistorico = linhas.count { "${it[0].trim().uppercase()}|${it[1].trim().uppercase()}" in conhecidos }
                    linhas = ordemHistorico(linhas, historico).map { linhas[it] }
                    aviso(contexto, "$comHistorico de ${linhas.size} linhas ordenadas pelo histórico; as restantes ficaram no fim.")
                }
                abrir(Store.importar(nomeDe(contexto, uri), linhas))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aviso(contexto, "Não foi possível ler o ficheiro. Tem de ser um Excel .xlsx com dados.")
            } finally {
                ocupado = false
            }
        }
    }
    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importarUri(uri) }

    val visiveis = estado.ficheiros.filter(noFiltro)
  Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        BarraTopo("MyPsi")
        filtro()
        if (ocupado) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            Text("A importar…", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (estado.ficheiros.isEmpty()) EstadoVazio("Ainda não há ficheiros", "Toque em Importar, no canto inferior direito, para carregar a primeira lista.")
        else if (visiveis.isEmpty()) EstadoVazio("Nenhum ficheiro neste período", "Escolha outro ano, mês ou quinzena no filtro acima, ou importe um ficheiro.")
        // espaço no fim para o botão flutuante não tapar o último ficheiro
        LazyColumn(Modifier.padding(top = 8.dp), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(visiveis.asReversed(), key = { it.id }) { f ->
                ListItem(
                    headlineContent = { Text(f.nome) },
                    supportingContent = {
                        Text(
                            "${f.categoria} · ${f.linhas.size} linhas\nimportado ${
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("pt", "PT")).format(Date(f.importadoEm))
                            }"
                        )
                    },
                    trailingContent = { BotaoApagar { apagar = f } },
                    modifier = Modifier.combinedClickable(
                        onClick = { abrir(f.id) },
                        onLongClick = { apagar = f },
                        onLongClickLabel = "Apagar ficheiro",
                    ),
                )
                HorizontalDivider()
            }
        }
    }
    ExtendedFloatingActionButton(
        onClick = { if (!ocupado) escolherOrdem = true },
        icon = { Icone(R.drawable.ic_importar, null, tint = androidx.compose.material3.LocalContentColor.current) },
        text = { Text(if (ocupado) "A importar…" else "Importar") },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    )
  }

    if (escolherOrdem) {
        fun importar(comModo: Int) {
            escolherOrdem = false
            modo = comModo
            val vindo = pendente
            pendente = null
            if (vindo != null) importarUri(vindo)
            // alguns gestores de ficheiros entregam o .xlsx como octet-stream
            else seletor.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream"))
        }
        AlertDialog(
            onDismissRequest = { escolherOrdem = false; pendente = null },
            title = { Text(pendente?.let { "Importar ${semExtensao(nomeDe(contexto, it))}" } ?: "Importar Excel") },
            text = {
                Column {
                    OutlinedButton(onClick = { importar(IMPORTAR_FICHEIRO) }, modifier = Modifier.fillMaxWidth()) { Text("Importar com a ordenação do ficheiro") }
                    OutlinedButton(onClick = { importar(IMPORTAR_ROTA) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Importar e ordenar a rota automaticamente")
                    }
                    OutlinedButton(
                        onClick = { importar(IMPORTAR_HISTORICO) },
                        enabled = estado.ficheiros.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Importar e ordenar pelo histórico dos ficheiros") }
                    if (estado.ficheiros.isEmpty()) Text(
                        "O histórico fica disponível depois do primeiro ficheiro.",
                        Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { escolherOrdem = false; pendente = null }) { Text("Cancelar") } },
        )
    }

    apagar?.let { f ->
        AlertDialog(
            onDismissRequest = { apagar = null },
            title = { Text("Apagar ficheiro?") },
            text = { Text("\"${f.nome}\" e todas as seleções e valores registados serão apagados.") },
            confirmButton = { TextButton(onClick = { Store.apagarFicheiro(f.id); apagar = null; desfazer("Ficheiro \"${f.nome}\" apagado") }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { apagar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PaginaFicheiro(ficheiro: Ficheiro, opcoes: List<Opcao>, pedido: PedidoLinha?, pedidoAtendido: () -> Unit, voltar: () -> Unit) {
    // linha cujo VALOR está a ser escrito: fica no sítio até se sair do campo
    var emEdicao by remember { mutableStateOf<Long?>(null) }
    // pesquisa: filtra as linhas por PDV, LOCAL, produto, OBS ou nota; com pesquisa ativa não se arrasta nem se recolhe
    var aPesquisar by rememberSaveable(ficheiro.id) { mutableStateOf(false) }
    var pesquisa by rememberSaveable(ficheiro.id) { mutableStateOf("") }
    val termo = normalizar(pesquisa.trim())
    fun corresponde(l: Linha) = termo.isEmpty() || normalizar("${l.pdv} ${l.local} ${l.produto} ${l.obs} ${l.nota}").contains(termo)
    val contexto = LocalContext.current
    val desfazer = LocalDesfazer.current
    val vibrar = LocalHapticFeedback.current
    var menuDe by remember { mutableStateOf<Long?>(null) }
    var notaDe by remember { mutableStateOf<Linha?>(null) }
    var editarDe by remember { mutableStateOf<Linha?>(null) } // id 0 = linha nova
    var apagarDe by remember { mutableStateOf<Linha?>(null) }
    // por tratar agrupadas por LOCAL e, no fim do ecrã, as tratadas; cópia local durante o arraste, gravada ao largar
    var linhas by remember(ficheiro.linhas, emEdicao) {
        mutableStateOf(ordemApresentacao(ficheiro.linhas, emEdicao).let { (porFazer, feitas) -> porFazer + feitas })
    }
    val idsTratadas = linhas.filter { tratada(it) && it.id != emEdicao }.map { it.id }.toSet()
    val porFazer = linhas.filter { it.id !in idsTratadas }
    val feitas = linhas.filter { it.id in idsTratadas }
    val estadoLista = rememberLazyListState()
    // chaves da lista: cabeçalho de grupo = "g:<local>", cabeçalho das tratadas = TRATADAS, linha = id (Long)
    fun grupoDe(chave: Any): String = when {
        chave == TRATADAS -> TRATADAS
        chave is String -> chave.removePrefix("g:")
        chave in idsTratadas -> TRATADAS
        else -> linhas.first { it.id == chave }.local
    }
    val reordenavel = rememberReorderableLazyListState(estadoLista) { de, para ->
        val grupoDe = grupoDe(de.key)
        val grupoPara = grupoDe(para.key)
        if (de.key is String) {
            // cabeçalho de LOCAL: move o grupo inteiro (só entre grupos por tratar)
            if (de.key != TRATADAS && grupoPara != grupoDe && grupoPara != TRATADAS && pesquisa.isBlank()) {
                val ordem = porFazer.map { it.local }.distinct().toMutableList()
                ordem.add(ordem.indexOf(grupoPara), ordem.removeAt(ordem.indexOf(grupoDe)))
                val grupos = porFazer.groupBy { it.local }
                linhas = ordem.flatMap { grupos.getValue(it) } + feitas
            }
        } else if (para.key is Long && grupoPara == grupoDe && pesquisa.isBlank()) {
            // linha: só dentro do mesmo grupo (e nunca com a lista filtrada pela pesquisa)
            val i = linhas.indexOfFirst { it.id == de.key }
            val j = linhas.indexOfFirst { it.id == para.key }
            linhas = linhas.toMutableList().apply { add(j, removeAt(i)) }
        }
    }
    val guardarOrdem = { Store.reordenar(ficheiro.id, linhas.map { it.id }) }
    // grupos recolhidos nesta página; não é gravado
    var recolhidos by rememberSaveable(ficheiro.id) { mutableStateOf(emptyList<String>()) }
    val locais = porFazer.map { it.local }.distinct() + listOfNotNull(TRATADAS.takeIf { feitas.isNotEmpty() })
    val todosRecolhidos = locais.isNotEmpty() && locais.all { it in recolhidos }
    fun alternar(grupo: String) { recolhidos = if (grupo in recolhidos) recolhidos - grupo else recolhidos + grupo }

    // chaves da LazyColumn pela ordem em que são desenhadas (para deslocar até uma linha)
    val gruposVisiveis = porFazer.groupBy { it.local }.map { (local, l) -> local to l } + listOfNotNull(feitas.takeIf { it.isNotEmpty() }?.let { TRATADAS to it })
    // linha cujo VALOR deve receber o cursor (toque no widget)
    var focoEm by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pedido) {
        val p = pedido ?: return@LaunchedEffect
        val linha = linhas.firstOrNull { it.id == p.linhaId }
        if (linha != null) {
            val grupo = if (linha.id in idsTratadas) TRATADAS else linha.local
            if (grupo in recolhidos) recolhidos = recolhidos - grupo
            withFrameNanos { } // espera que o grupo desdobrado entre na lista
            val i = gruposVisiveis.flatMap { (g, l) -> listOf<Any>(if (g == TRATADAS) TRATADAS else "g:$g") + if (g in recolhidos) emptyList() else l.map { it.id } }.indexOf(linha.id)
            if (i >= 0) estadoLista.scrollToItem(i)
            focoEm = linha.id
        }
        pedidoAtendido()
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = voltar, modifier = Modifier.size(56.dp)) {
                Icon(
                    painterResource(R.drawable.ic_voltar),
                    contentDescription = "Voltar aos ficheiros",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(ficheiro.nome, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!aPesquisar) IconButton(onClick = { editarDe = Linha(0, "", "", "", "") }, modifier = Modifier.size(48.dp)) {
                Icone(R.drawable.ic_adicionar, "Acrescentar linha", Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { aPesquisar = !aPesquisar; if (!aPesquisar) pesquisa = "" }, modifier = Modifier.size(48.dp)) {
                Icone(
                    if (aPesquisar) R.drawable.ic_fechar else R.drawable.ic_pesquisar,
                    if (aPesquisar) "Fechar a pesquisa" else "Pesquisar",
                    Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (!aPesquisar) IconButton(onClick = { recolhidos = if (todosRecolhidos) emptyList() else locais }, modifier = Modifier.size(48.dp)) {
                Icone(
                    if (todosRecolhidos) R.drawable.ic_expandir_todos else R.drawable.ic_recolher_todos,
                    if (todosRecolhidos) "Expandir todos os grupos" else "Recolher todos os grupos",
                    Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (aPesquisar) {
            val foco = remember { FocusRequester() }
            LaunchedEffect(Unit) { foco.requestFocus() }
            OutlinedTextField(
                value = pesquisa,
                onValueChange = { pesquisa = it },
                placeholder = { Text("PDV, LOCAL, produto, OBS ou nota") },
                leadingIcon = { Icone(R.drawable.ic_pesquisar, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(foco),
            )
            if (termo.isNotEmpty()) Text(
                "${linhas.count(::corresponde)} de ${linhas.size} linhas",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // tudo cabe na largura do ecrã: PDV/PRODUTO/OBS empilhados numa coluna elástica,
        // opções e VALOR em colunas estreitas repartidas pelo espaço que sobra
        BoxWithConstraints(Modifier.weight(1f)) {
            val livre = maxWidth - LARG_PEGA - LARG_VALOR - MIN_TEXTO
            val largOpcao = if (opcoes.isEmpty()) 0.dp else (livre / opcoes.size).coerceIn(MIN_OPCAO, MAX_OPCAO)
            Column(Modifier.fillMaxSize()) {
                // cabeçalho das colunas em tom neutro, para não se confundir com os cabeçalhos de grupo (cor)
                Row(Modifier.background(MaterialTheme.colorScheme.surface), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(LARG_PEGA))
                    Spacer(Modifier.weight(1f)) // coluna PDV/PRODUTO/OBS sem título
                    opcoes.forEach { Cabecalho("${it.valor} €".takeIf { _ -> parseCentimos(it.valor) != null } ?: it.valor, Modifier.width(largOpcao)) }
                    Cabecalho("VALOR", Modifier.width(LARG_VALOR))
                }
                HorizontalDivider()
                LazyColumn(state = estadoLista) {
                  // grupos por tratar e, no fim, a secção das tratadas (mesma estrutura, outra cor)
                  val grupos = (porFazer.groupBy { it.local }.map { (local, l) -> Triple("g:$local", local.ifEmpty { "(sem local)" }, l) } +
                      listOfNotNull(feitas.takeIf { it.isNotEmpty() }?.let { Triple(TRATADAS, "Tratadas", it) }))
                      .map { (chave, titulo, l) -> Triple(chave, titulo, l.filter(::corresponde)) }
                      .filter { it.third.isNotEmpty() } // com pesquisa, grupos sem resultados desaparecem
                  val semArrastar = termo.isNotEmpty()
                  grupos.forEach { (chave, titulo, doGrupo) ->
                    val grupo = if (chave == TRATADAS) TRATADAS else chave.removePrefix("g:")
                    val ehTratadas = chave == TRATADAS
                    item(key = chave) {
                        ReorderableItem(reordenavel, key = chave, enabled = !ehTratadas) {
                            val fundo = if (ehTratadas) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                            val texto = if (ehTratadas) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                            Row(Modifier.fillMaxWidth().background(fundo), verticalAlignment = Alignment.CenterVertically) {
                                if (ehTratadas || semArrastar) Spacer(Modifier.width(LARG_PEGA))
                                else Pega("Arrastar o grupo $titulo", texto) { draggableHandle(onDragStopped = { guardarOrdem() }) }
                                val recolhido = grupo in recolhidos && !semArrastar
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .clickable(onClickLabel = if (recolhido) "Expandir" else "Recolher") { alternar(grupo) }
                                        .padding(end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "$titulo (${doGrupo.size})",
                                        Modifier.weight(1f).padding(vertical = 12.dp),
                                        color = texto,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Icone(if (recolhido) R.drawable.ic_recolhido else R.drawable.ic_expandir, null, tint = texto)
                                }
                            }
                        }
                    }
                    if (grupo !in recolhidos || semArrastar) items(doGrupo, key = { it.id }) { linha ->
                        ReorderableItem(reordenavel, key = linha.id) {
                            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (semArrastar) Spacer(Modifier.width(LARG_PEGA))
                                    else Pega("Arrastar ${linha.pdv} para reordenar", MaterialTheme.colorScheme.onSurfaceVariant) {
                                        draggableHandle(onDragStopped = { guardarOrdem() })
                                    }
                                    // tocar no texto abre o menu da linha (navegar até ao PDV, nota)
                                    Box(Modifier.weight(1f)) {
                                        Column(
                                            Modifier.fillMaxWidth().clickable(onClickLabel = "Mais ações") { menuDe = linha.id }.padding(vertical = 6.dp),
                                        ) {
                                            Text(linha.pdv, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            // nas tratadas o LOCAL deixa de estar no cabeçalho: mostra-o junto ao produto
                                            Text(
                                                if (ehTratadas) "${linha.local} · ${linha.produto}" else linha.produto,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            if (linha.obs.isNotEmpty()) Text(
                                                linha.obs,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            if (linha.nota.isNotEmpty()) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icone(R.drawable.ic_nota, null, Modifier.size(14.dp))
                                                Text(
                                                    linha.nota,
                                                    Modifier.padding(start = 4.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontStyle = FontStyle.Italic,
                                                )
                                            }
                                        }
                                        DropdownMenu(expanded = menuDe == linha.id, onDismissRequest = { menuDe = null }) {
                                            DropdownMenuItem(
                                                text = { Text("Navegar até ao PDV") },
                                                leadingIcon = { Icone(R.drawable.ic_navegar, null) },
                                                onClick = { menuDe = null; navegarAte(contexto, linha) },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (linha.nota.isEmpty()) "Escrever nota" else "Editar nota") },
                                                leadingIcon = { Icone(R.drawable.ic_nota, null) },
                                                onClick = { menuDe = null; notaDe = linha },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Editar linha") },
                                                leadingIcon = { Icone(R.drawable.ic_nota, null) },
                                                onClick = { menuDe = null; editarDe = linha },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Apagar linha", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icone(R.drawable.ic_apagar, null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = { menuDe = null; apagarDe = linha },
                                            )
                                        }
                                    }
                                    opcoes.forEach { o ->
                                        Box(Modifier.width(largOpcao), contentAlignment = Alignment.Center) {
                                            RadioButton(
                                                selected = linha.centimos == null && linha.opcaoId == o.id, // valor manual substitui a opção
                                                onClick = {
                                                    val desmarcar = linha.centimos == null && linha.opcaoId == o.id
                                                    Store.escolherOpcao(ficheiro.id, linha.id, o.id)
                                                    vibrar.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    val valor = if (parseCentimos(o.valor) != null) "${o.valor} €" else o.valor
                                                    desfazer(if (desmarcar) "${linha.pdv}: seleção retirada" else "${linha.pdv}: $valor")
                                                },
                                                modifier = Modifier.semantics { contentDescription = o.valor },
                                            )
                                        }
                                    }
                                    // só uma linha por tratar fica no sítio enquanto se escreve; uma tratada já está no fim
                                    CampoValor(ficheiro.id, linha, pedirFoco = focoEm == linha.id, focoPedidoFeito = { focoEm = null }) { focado ->
                                        emEdicao = when {
                                            focado && !tratada(linha) -> linha.id
                                            !focado && emEdicao == linha.id -> null
                                            else -> emEdicao
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                  }
                }
            }
        }
    }

    editarDe?.let { l ->
        val nova = l.id == 0L
        val campos = remember(l.id) { mutableStateListOf(l.local, l.pdv, l.produto, l.obs) }
        AlertDialog(
            onDismissRequest = { editarDe = null },
            title = { Text(if (nova) "Acrescentar linha" else "Editar linha") },
            text = {
                Column {
                    listOf("LOCAL", "PDV", "PRODUTO", "OBS").forEachIndexed { i, nome ->
                        OutlinedTextField(
                            value = campos[i],
                            onValueChange = { campos[i] = it },
                            label = { Text(if (i == 1) "$nome *" else nome) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                    // atalho: escolher um LOCAL que já existe no ficheiro
                    val existentes = linhas.map { it.local }.filter { it.isNotBlank() }.distinct()
                    if (existentes.isNotEmpty() && nova) Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())) {
                        existentes.forEach { loc -> AssistChip(onClick = { campos[0] = loc }, label = { Text(loc) }, modifier = Modifier.padding(end = 6.dp)) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = campos[1].isNotBlank(), onClick = {
                    if (nova) Store.adicionarLinha(ficheiro.id, campos.toList()) else Store.editarLinha(ficheiro.id, l.id, campos.toList())
                    editarDe = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editarDe = null }) { Text("Cancelar") } },
        )
    }

    apagarDe?.let { l ->
        AlertDialog(
            onDismissRequest = { apagarDe = null },
            title = { Text("Apagar linha?") },
            text = { Text("${l.pdv} · ${l.produto} será apagada deste ficheiro.") },
            confirmButton = {
                TextButton(onClick = { Store.apagarLinha(ficheiro.id, l.id); apagarDe = null; desfazer("Linha ${l.pdv} apagada") }) {
                    Text("Apagar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { apagarDe = null }) { Text("Cancelar") } },
        )
    }

    notaDe?.let { l ->
        var texto by remember(l.id) { mutableStateOf(l.nota) }
        AlertDialog(
            onDismissRequest = { notaDe = null },
            title = { Text("Nota · ${l.pdv}") },
            text = {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    placeholder = { Text("Ex.: voltar na terça") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { Store.definirNota(ficheiro.id, l.id, texto); notaDe = null }) { Text("Guardar") } },
            dismissButton = {
                Row {
                    if (l.nota.isNotEmpty()) TextButton(onClick = { Store.definirNota(ficheiro.id, l.id, ""); notaDe = null }) { Text("Apagar nota") }
                    TextButton(onClick = { notaDe = null }) { Text("Cancelar") }
                }
            },
        )
    }
}

/** Minúsculas e sem acentos, para a pesquisa encontrar "sao jose" em "SÃO JOSÉ". */
private fun normalizar(texto: String): String =
    java.text.Normalizer.normalize(texto.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Abre o Google Maps (ou outra app de mapas) com o caminho até à farmácia da linha. */
private fun navegarAte(contexto: Context, l: Linha) {
    val destino = Uri.encode("Farmácia ${l.pdv}, ${l.local}, Portugal")
    val intencao = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destino&travelmode=driving"))
    runCatching { contexto.startActivity(intencao) }.onFailure { aviso(contexto, "Não há nenhuma app de mapas para abrir o caminho.") }
}

@Composable
private fun Cabecalho(texto: String, modifier: Modifier, alinhamento: TextAlign = TextAlign.Center) = Text(
    texto,
    modifier.padding(horizontal = 2.dp, vertical = 8.dp),
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = alinhamento,
    style = MaterialTheme.typography.labelSmall,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
)

@Composable
private fun CampoValor(ficheiroId: Long, linha: Linha, pedirFoco: Boolean = false, focoPedidoFeito: () -> Unit = {}, foco: (Boolean) -> Unit) {
    val gestorFoco = LocalFocusManager.current
    val pedidoFoco = remember { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current
    LaunchedEffect(pedirFoco) {
        if (pedirFoco) {
            pedidoFoco.requestFocus()
            teclado?.show()
            focoPedidoFeito()
        }
    }
    var texto by remember { mutableStateOf(linha.centimos?.let(::formatCentimos) ?: "") }
    // escolher uma opção limpa o valor no Store: o campo acompanha
    LaunchedEffect(linha.centimos) { if (linha.centimos == null && parseCentimos(texto) != null) texto = "" }
    BasicTextField(
        value = texto,
        onValueChange = {
            if (VALOR_VALIDO.matches(it)) {
                texto = it
                // grava a cada tecla: uma linha que saia do ecrã a meio da edição não perde o valor
                Store.definirValor(ficheiroId, linha.id, parseCentimos(it))
            }
        },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { gestorFoco.clearFocus() }),
        decorationBox = { campo ->
            Box(contentAlignment = Alignment.CenterEnd) {
                if (texto.isEmpty()) Text("0,00 €", style = LocalTextStyle.current, color = MaterialTheme.colorScheme.outline)
                campo()
            }
        },
        modifier = Modifier
            .width(LARG_VALOR)
            .padding(4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(8.dp)
            .semantics { contentDescription = "Valor" }
            .focusRequester(pedidoFoco)
            .onFocusChanged {
                foco(it.isFocused)
                if (!it.isFocused) texto = parseCentimos(texto)?.let(::formatCentimos) ?: "" // normaliza para 2 casas
            },
    )
}

/** Pega de arraste com 48dp de área de toque. */
@Composable
private fun Pega(descricao: String, cor: Color, arrastar: Modifier.() -> Modifier) = Box(
    Modifier.size(LARG_PEGA).arrastar().semantics { contentDescription = descricao },
    contentAlignment = Alignment.Center,
) { Icone(R.drawable.ic_arrastar, null, tint = cor) }

/** Ícone vermelho de caixote do lixo; usado nas listas de ficheiros e de opções. */
@Composable
fun BotaoApagar(onClick: () -> Unit) = IconButton(onClick = onClick) {
    Icon(painterResource(R.drawable.ic_apagar), contentDescription = "Apagar", tint = MaterialTheme.colorScheme.error)
}

private fun aviso(contexto: Context, texto: String) = Toast.makeText(contexto, texto, Toast.LENGTH_LONG).show()

private fun nomeDe(contexto: Context, uri: Uri): String =
    contexto.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
        ?: uri.lastPathSegment ?: "Ficheiro"
