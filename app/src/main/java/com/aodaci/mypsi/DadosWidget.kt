package com.aodaci.mypsi

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.RadioButton
import androidx.glance.appwidget.RadioButtonDefaults
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as CorDiaNoite

private val FICHEIRO = ActionParameters.Key<Long>("ficheiro")
private val LINHA = ActionParameters.Key<Long>("linha")
private val OPCAO = ActionParameters.Key<Long>("opcao")
// nomes iguais aos extras lidos pela MainActivity (o Glance passa os parâmetros como extras do Intent)
private val ABRIR_FICHEIRO = ActionParameters.Key<Long>(EXTRA_FICHEIRO)
private val ABRIR_LINHA = ActionParameters.Key<Long>(EXTRA_LINHA)

/** Lista do último ficheiro importado: igual à página Dados, sem a coluna VALOR. */
class DadosWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        provideContent {
            val estado by Store.estado.collectAsState()
            Conteudo(estado)
        }
    }
}

/** Cores do widget: o mesmo esquema da app (cor base + tema escolhido nas Opções). */
private class Cores(estado: Estado) {
    private val claro = esquema(Color(estado.corBase), false)
    private val escuro = esquema(Color(estado.corBase), true)
    private val tema = estado.tema
    fun de(f: (ColorScheme) -> Color): ColorProvider = when (tema) {
        TEMA_CLARO -> ColorProvider(f(claro))
        TEMA_ESCURO -> ColorProvider(f(escuro))
        else -> CorDiaNoite(day = f(claro), night = f(escuro))
    }
}

@Composable
private fun Conteudo(estado: Estado) {
    val cores = Cores(estado)
    val ficheiro = estado.ficheiros.lastOrNull()
    val opcoes = estado.opcoes.filter { it.visivel }
    val letra = estado.letraWidget
    val normal = TextStyle(color = cores.de { it.onSurface }, fontSize = letra.sp)
    val negrito = normal.copy(fontWeight = FontWeight.Bold)
    val secundario = normal.copy(color = cores.de { it.onSurfaceVariant })

    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(20.dp).background(cores.de { it.surface }),
    ) {
        // cabeçalho: logótipo + ficheiro e período (abre a app) + botão de letra
        Row(GlanceModifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(36.dp).cornerRadius(18.dp).clickable(actionStartActivity<MainActivity>())) {
                Image(ImageProvider(R.drawable.ic_launcher_background), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
                Image(ImageProvider(R.drawable.ic_launcher_foreground), contentDescription = "Abrir MyPsi", modifier = GlanceModifier.fillMaxSize())
            }
            Column(GlanceModifier.defaultWeight().padding(horizontal = 10.dp).clickable(actionStartActivity<MainActivity>())) {
                Text(ficheiro?.nome ?: "MyPsi", style = negrito.copy(fontSize = (letra + 2).sp), maxLines = 1)
                Text(ficheiro?.categoria?.toString() ?: "Sem ficheiros importados", style = secundario.copy(fontSize = (letra - 2).coerceAtLeast(10).sp), maxLines = 1)
            }
            Box(
                GlanceModifier.size(44.dp).cornerRadius(22.dp).background(cores.de { it.primaryContainer }).clickable(actionRunCallback<LetraAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    ImageProvider(R.drawable.ic_letra),
                    contentDescription = "Aumentar a letra",
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(cores.de { it.onPrimaryContainer }),
                )
            }
        }

        // mesma ordem da página do ficheiro: por tratar agrupadas por LOCAL, tratadas no fim
        val (porFazer, feitas) = ordemApresentacao(ficheiro?.linhas ?: emptyList())
        val linhas = porFazer + feitas
        if (ficheiro == null) Text(
            "Importe um ficheiro na app para o ver aqui.",
            GlanceModifier.fillMaxWidth().padding(16.dp).clickable(actionStartActivity<MainActivity>()),
            style = secundario,
        )
        LazyColumn(GlanceModifier.fillMaxSize()) {
            items(linhas.withIndex().toList(), itemId = { it.value.id }) { (i, l) ->
                val ehTratada = i >= porFazer.size
                val novoGrupo = if (ehTratada) i == porFazer.size else i == 0 || linhas[i - 1].local != l.local
                Column(GlanceModifier.fillMaxWidth()) {
                    // um cabeçalho por LOCAL e um para as tratadas, como na página do ficheiro
                    if (novoGrupo && ehTratada) Text(
                        "Tratadas (${feitas.size})",
                        GlanceModifier.fillMaxWidth().background(cores.de { it.surfaceVariant }).padding(horizontal = 12.dp, vertical = 6.dp),
                        style = negrito.copy(color = cores.de { it.onSurfaceVariant }),
                    )
                    else if (novoGrupo) Text(
                        "${l.local.ifEmpty { "(sem local)" }} (${porFazer.count { it.local == l.local }})",
                        GlanceModifier.fillMaxWidth().background(cores.de { it.primaryContainer }).padding(horizontal = 12.dp, vertical = 6.dp),
                        style = negrito.copy(color = cores.de { it.onPrimaryContainer }),
                    )
                    else Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(cores.de { it.outlineVariant }))
                    Column(GlanceModifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        // tocar no texto da linha abre a app com o cursor no VALOR desta linha
                        Column(
                            GlanceModifier.fillMaxWidth().clickable(
                                actionStartActivity<MainActivity>(actionParametersOf(ABRIR_FICHEIRO to ficheiro!!.id, ABRIR_LINHA to l.id))
                            ),
                        ) {
                            Text(l.pdv, style = negrito)
                            Text(if (ehTratada) "${l.local} · ${l.produto}" else l.produto, style = secundario)
                            if (l.obs.isNotEmpty()) Text(l.obs, style = negrito.copy(color = cores.de { it.primary }))
                            if (l.nota.isNotEmpty()) Text("Nota: ${l.nota}", style = secundario.copy(fontStyle = androidx.glance.text.FontStyle.Italic))
                        }
                        if (opcoes.isNotEmpty()) Row(GlanceModifier.padding(top = 4.dp)) {
                            opcoes.forEach { o ->
                                RadioButton(
                                    checked = l.centimos == null && l.opcaoId == o.id, // valor manual substitui a opção
                                    onClick = actionRunCallback<EscolherAction>(
                                        actionParametersOf(FICHEIRO to ficheiro!!.id, LINHA to l.id, OPCAO to o.id)
                                    ),
                                    text = if (parseCentimos(o.valor) != null) "${o.valor} €" else o.valor,
                                    style = normal,
                                    colors = RadioButtonDefaults.colors(
                                        checkedColor = cores.de { it.primary },
                                        uncheckedColor = cores.de { it.onSurfaceVariant },
                                    ),
                                    modifier = GlanceModifier.padding(end = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class EscolherAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        Store.escolherOpcao(parameters[FICHEIRO] ?: return, parameters[LINHA] ?: return, parameters[OPCAO] ?: return)
    }
}

class LetraAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        Store.proximaLetraWidget()
    }
}

class DadosWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DadosWidget()
}
