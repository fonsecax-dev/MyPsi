package com.aodaci.mypsi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Slider
import androidx.compose.material3.OutlinedButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.net.Uri
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val PERMISSAO_PASTA = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION

private val ARCO_IRIS =listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/** Escolha livre de cor: tom, saturação e brilho (HSV), com pré-visualização. */
@Composable
private fun Paleta(inicial: Long, fechar: () -> Unit, escolher: (Long) -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(inicial.toInt(), it) } }
    var tom by remember { mutableFloatStateOf(hsv[0]) }
    var saturacao by remember { mutableFloatStateOf(hsv[1].coerceAtLeast(0.3f)) }
    var brilho by remember { mutableFloatStateOf(hsv[2].coerceIn(0.3f, 0.9f)) }
    val cor = Color(android.graphics.Color.HSVToColor(floatArrayOf(tom, saturacao, brilho)))
    AlertDialog(
        onDismissRequest = fechar,
        title = { Text("Escolher cor") },
        text = {
            Column {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(cor),
                    contentAlignment = Alignment.Center,
                ) { Text("Exemplo", color = Color.White, fontWeight = FontWeight.Bold) }
                Subtitulo("Tom")
                Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(ARCO_IRIS)))
                Slider(tom, { tom = it }, valueRange = 0f..360f, modifier = Modifier.semantics { contentDescription = "Tom" })
                Subtitulo("Saturação")
                Slider(saturacao, { saturacao = it }, valueRange = 0.3f..1f, modifier = Modifier.semantics { contentDescription = "Saturação" })
                Subtitulo("Brilho")
                // limites: cores muito claras ficam ilegíveis com texto branco por cima
                Slider(brilho, { brilho = it }, valueRange = 0.3f..0.9f, modifier = Modifier.semantics { contentDescription = "Brilho" })
            }
        },
        confirmButton = { TextButton(onClick = { escolher(cor.toArgb().toLong() and 0xFFFFFFFFL) }) { Text("Usar esta cor") } },
        dismissButton = { TextButton(onClick = fechar) { Text("Cancelar") } },
    )
}

@Composable
private fun Subtitulo(texto: String) = Text(
    texto,
    Modifier.padding(top = 16.dp),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OpcoesScreen(estado: Estado) {
    var morada by remember { mutableStateOf(estado.moradaPartida) }
    var editar by remember { mutableStateOf<Opcao?>(null) } // id 0 = nova
    var apagar by remember { mutableStateOf<Opcao?>(null) }
    var confirmarImportar by remember { mutableStateOf(false) }
    var paleta by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    fun aviso(texto: String) = Toast.makeText(contexto, texto, Toast.LENGTH_LONG).show()
    // shortcut: lê/escreve no thread principal; a cópia tem poucas dezenas de KB. Passar para Dispatchers.IO se crescer muito.
    val exportador = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { contexto.contentResolver.openOutputStream(uri)!!.use { it.write(Store.exportar().toByteArray()) } }
            .onSuccess { aviso("Dados exportados.") }
            .onFailure { aviso("Não foi possível exportar os dados.") }
    }
    // lembrete: no Android 13+ é preciso autorização para notificações
    val pedirNotificacoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { autorizado ->
        if (autorizado) { Store.definirLembrete(true, Store.estado.value.horaLembrete); Lembrete.agendar(contexto) }
        else aviso("Sem autorização para notificações o lembrete não pode funcionar.")
    }
    // pasta da cópia automática: fica com permissão permanente e faz logo a primeira cópia
    val escolherPasta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contexto.contentResolver.takePersistableUriPermission(uri, PERMISSAO_PASTA)
            Store.definirPastaCopia(uri.toString())
            CopiaAutomatica.fazerAgora(contexto, uri.toString()) { ok ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    aviso(if (ok) "Cópia automática ligada; primeira cópia feita." else "Não foi possível gravar nessa pasta.")
                }
            }
        }
    }
    val importador = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { Store.importarCopia(contexto.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }) }
            .onSuccess { morada = Store.estado.value.moradaPartida; aviso("Dados importados.") }
            .onFailure { aviso("Ficheiro inválido: não é uma cópia de segurança do MyPsi. Nada foi alterado.") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        BarraTopo("Opções")
      Seccao("Aparência") {
        Subtitulo("Tema")
        val temas = listOf(TEMA_SISTEMA to "Sistema", TEMA_CLARO to "Claro", TEMA_ESCURO to "Escuro")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            temas.forEachIndexed { i, (tema, nome) ->
                SegmentedButton(
                    selected = estado.tema == tema,
                    onClick = { Store.definirTema(tema) },
                    shape = SegmentedButtonDefaults.itemShape(i, temas.size),
                ) { Text(nome, maxLines = 1) }
            }
        }
        Subtitulo("Cor base")
        // FlowRow: passa para a linha seguinte em vez de sair do ecrã
        FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CORES.forEach { (nome, argb) ->
                val escolhida = estado.corBase == argb
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(3.dp, if (escolhida) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                        .selectable(selected = escolhida, role = Role.RadioButton) { Store.definirCor(argb) }
                        .semantics { contentDescription = nome },
                    contentAlignment = Alignment.Center,
                ) { if (escolhida) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) }
            }
            // cor personalizada: arco-íris até ser escolhida, depois mostra a própria cor
            val personalizada = CORES.none { it.second == estado.corBase }
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .then(if (personalizada) Modifier.background(Color(estado.corBase)) else Modifier.background(Brush.sweepGradient(ARCO_IRIS)))
                    .border(3.dp, if (personalizada) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .clickable(onClickLabel = "Escolher outra cor") { paleta = true }
                    .semantics { contentDescription = "Cor personalizada" },
                contentAlignment = Alignment.Center,
            ) {
                if (personalizada) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                else Icone(R.drawable.ic_adicionar, null, tint = Color.White)
            }
        }
        // valor local enquanto se arrasta; grava (e aplica à app) só ao largar
        var escala by remember(estado.escalaTexto) { mutableFloatStateOf(estado.escalaTexto) }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tamanho do texto", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(escala * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", fontSize = 14.sp)
            Slider(
                value = escala,
                onValueChange = { escala = it },
                onValueChangeFinished = { Store.definirEscala(escala) },
                valueRange = ESCALA_MIN..ESCALA_MAX,
                steps = 15, // passos de 5%
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = "Tamanho do texto" },
            )
            Text("A", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
      }

      Seccao("Rota") {
        OutlinedTextField(
            value = morada,
            onValueChange = { morada = it; Store.definirMorada(it.trim()) },
            label = { Text("Morada de partida") },
            supportingText = { Text("Usada ao importar com \"ordenar a rota automaticamente\".") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
      }

      Seccao("Colunas de opção", acao = {
          FilledTonalButton(onClick = { editar = Opcao(0, "", true) }) {
              Icone(R.drawable.ic_adicionar, null, Modifier.padding(end = 4.dp), tint = androidx.compose.material3.LocalContentColor.current)
              Text("Adicionar")
          }
      }) {
        Text(
            "Valor em euros pago por linha. As visíveis aparecem como colunas em Dados e no widget.",
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (estado.opcoes.isEmpty()) Text("Ainda não há opções.", Modifier.padding(top = 12.dp))
        estado.opcoes.forEachIndexed { i, o ->
            if (i > 0) HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Editar") { editar = o }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(parseCentimos(o.valor)?.let { "${formatCentimos(it)} €" } ?: o.valor, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (o.visivel) "Visível" else "Oculta",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = o.visivel,
                    onCheckedChange = { Store.guardarOpcao(o.id, o.valor, it) },
                    modifier = Modifier.semantics { contentDescription = "Visível" },
                )
                BotaoApagar { apagar = o }
            }
        }
      }

      Seccao("Lembrete") {
        val hora = "%02d:%02d".format(estado.horaLembrete / 60, estado.horaLembrete % 60)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lembrete diário", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (estado.lembrete) "Às $hora, se o último ficheiro tiver linhas por tratar." else "Desligado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = estado.lembrete,
                onCheckedChange = { ligar ->
                    if (ligar && android.os.Build.VERSION.SDK_INT >= 33) pedirNotificacoes.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else { Store.definirLembrete(ligar, estado.horaLembrete); Lembrete.agendar(contexto) }
                },
                modifier = Modifier.semantics { contentDescription = "Lembrete diário" },
            )
        }
        if (estado.lembrete) OutlinedButton(
            onClick = {
                android.app.TimePickerDialog(contexto, { _, h, m ->
                    Store.definirLembrete(true, h * 60 + m); Lembrete.agendar(contexto)
                }, estado.horaLembrete / 60, estado.horaLembrete % 60, true).show()
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Hora: $hora") }
      }

      Seccao("Cópia de segurança") {
        Text(
            "Exporta ou importa todos os dados: ficheiros, seleções, valores, opções e definições.",
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            OutlinedButton(
                onClick = { exportador.launch("MyPsi-copia-${java.time.LocalDate.now()}.json") },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            ) { Text("Exportar", maxLines = 1) }
            OutlinedButton(onClick = { confirmarImportar = true }, modifier = Modifier.weight(1f)) { Text("Importar", maxLines = 1) }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cópia automática diária", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        estado.pastaCopia.isBlank() -> "Desligada. Ligue para escolher a pasta (ex.: Drive ou Transferências)."
                        estado.ultimaCopia == 0L -> "Ligada · ainda sem cópias"
                        else -> "Ligada · última cópia ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("pt", "PT")).format(Date(estado.ultimaCopia))}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = estado.pastaCopia.isNotBlank(),
                onCheckedChange = { ligar ->
                    if (ligar) escolherPasta.launch(null)
                    else {
                        runCatching { contexto.contentResolver.releasePersistableUriPermission(Uri.parse(estado.pastaCopia), PERMISSAO_PASTA) }
                        Store.definirPastaCopia("")
                    }
                },
                modifier = Modifier.semantics { contentDescription = "Cópia automática diária" },
            )
        }
      }

        val versao = remember { runCatching { contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName }.getOrNull() }
        Text(
            "Desenvolvido para Susana Baltazar por Sérgio Fonseca",
            Modifier.fillMaxWidth().padding(top = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            "MyPsi · versão ${versao ?: "?"}",
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }

    if (paleta) Paleta(estado.corBase, fechar = { paleta = false }) { Store.definirCor(it); paleta = false }

    if (confirmarImportar) AlertDialog(
        onDismissRequest = { confirmarImportar = false },
        title = { Text("Importar dados?") },
        text = { Text("Todos os dados atuais serão substituídos pelos da cópia de segurança. Não é possível desfazer.") },
        confirmButton = {
            TextButton(onClick = {
                confirmarImportar = false
                importador.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            }) { Text("Escolher ficheiro") }
        },
        dismissButton = { TextButton(onClick = { confirmarImportar = false }) { Text("Cancelar") } },
    )

    editar?.let { o ->
        var valor by remember(o) { mutableStateOf(o.valor) }
        var visivel by remember(o) { mutableStateOf(o.visivel) }
        AlertDialog(
            onDismissRequest = { editar = null },
            title = { Text(if (o.id == 0L) "Nova opção" else "Editar opção") },
            text = {
                Column {
                    OutlinedTextField(value = valor, onValueChange = { valor = it }, label = { Text("Valor") }, singleLine = true)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Visível", Modifier.weight(1f))
                        Switch(checked = visivel, onCheckedChange = { visivel = it })
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valor.isNotBlank(),
                    onClick = { Store.guardarOpcao(o.id.takeIf { it != 0L }, valor.trim(), visivel); editar = null },
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editar = null }) { Text("Cancelar") } },
        )
    }

    apagar?.let { o ->
        val usos = usosOpcao(estado, o.id)
        if (usos.isNotEmpty()) AlertDialog(
            onDismissRequest = { apagar = null },
            title = { Text("Não é possível apagar") },
            text = {
                Text(
                    "\"${o.valor}\" está selecionada em ${usos.values.sum()} linha(s):\n" +
                        usos.entries.joinToString("\n") { (nome, n) -> "• $nome: $n" } +
                        "\n\nPara deixar de a mostrar, desligue \"Visível\"."
                )
            },
            confirmButton = { TextButton(onClick = { apagar = null }) { Text("OK") } },
        ) else AlertDialog(
            onDismissRequest = { apagar = null },
            title = { Text("Apagar opção?") },
            text = { Text("\"${o.valor}\" não está a ser usada em nenhuma linha e será apagada.") },
            confirmButton = { TextButton(onClick = { Store.apagarOpcao(o.id); apagar = null }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { apagar = null }) { Text("Cancelar") } },
        )
    }
}
