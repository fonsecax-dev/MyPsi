package com.aodaci.mypsi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private fun euros(centimos: Long) = "${formatCentimos(centimos)} €"
private const val TOPO = 5 // linhas mostradas antes de "Ver todos"

@Composable
fun DashboardScreen(estado: Estado, filtro: @Composable () -> Unit, doPeriodo: List<Ficheiro>) {
    if (doPeriodo.isEmpty()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            BarraTopo("Dashboard")
            filtro()
            if (estado.ficheiros.isEmpty()) EstadoVazio("Ainda não há dados", "Importe um ficheiro em Dados para ver aqui os totais.")
            else EstadoVazio("Nenhum ficheiro neste período", "Escolha outro ano, mês ou quinzena no filtro acima.")
        }
        return
    }
    // cada linha é paga OU pelo valor da opção (euros; texto não numérico conta 0) OU pelo valor manual, que a substitui
    val precoOpcao = estado.opcoes.associate { it.id to (parseCentimos(it.valor) ?: 0L) }
    fun manual(l: Linha) = l.centimos != null
    fun pagoOpcao(l: Linha) = if (manual(l)) 0L else l.opcaoId?.let { precoOpcao[it] } ?: 0L
    fun total(l: Linha) = l.centimos ?: pagoOpcao(l)
    fun total(ls: List<Linha>) = ls.sumOf(::total)
    fun tratada(l: Linha) = manual(l) || l.opcaoId != null
    fun fracao(parte: Long, todo: Long) = if (todo <= 0) 0f else (parte.toFloat() / todo).coerceIn(0f, 1f)

    val linhas = doPeriodo.flatMap { it.linhas } // todos os ficheiros do período do filtro
    val tratadas = linhas.count(::tratada)
    val totalOpcoes = linhas.sumOf(::pagoOpcao)
    val totalExtras = linhas.sumOf { it.centimos ?: 0L }
    val totalGeral = totalOpcoes + totalExtras

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        BarraTopo("Dashboard")
        filtro()
        val contexto = LocalContext.current
        OutlinedButton(
            onClick = {
                val cats = doPeriodo.map { it.categoria }.distinct()
                val periodo = when {
                    cats.size == 1 -> cats.single().toString()
                    cats.map { it.mes }.distinct().size == 1 -> "${MESES[cats.first().mes - 1]} ${cats.first().ano}"
                    else -> cats.first().ano.toString()
                }
                runCatching { Resumo.partilhar(contexto, periodo, doPeriodo, estado.opcoes) }
                    .onFailure { android.widget.Toast.makeText(contexto, "Não foi possível gerar o resumo.", android.widget.Toast.LENGTH_LONG).show() }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icone(R.drawable.ic_partilhar, null, Modifier.padding(end = 8.dp), tint = androidx.compose.material3.LocalContentColor.current)
            Text("Enviar resumo (PDF)")
        }

        // totais por quinzena (todos os ficheiros) para a variação e para o gráfico de evolução
        val porPeriodo = estado.ficheiros.groupBy { it.categoria }.mapValues { (_, fs) -> total(fs.flatMap { it.linhas }) }
        val ordemPeriodos = porPeriodo.keys.sortedWith(compareBy({ it.ano }, { it.mes }, { it.quinzena }))
        val categorias = doPeriodo.map { it.categoria }.distinct()
        // variação só faz sentido quando se vê uma única quinzena
        val atual = categorias.singleOrNull()
        val anterior = atual?.let { a -> ordemPeriodos.getOrNull(ordemPeriodos.indexOf(a) - 1) }

        // resumo em destaque
        Card(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Total a receber", style = MaterialTheme.typography.labelLarge)
                Text(euros(totalGeral), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                if (anterior != null) Variacao(totalGeral, porPeriodo.getValue(anterior), "vs $anterior")
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Numero("Linhas tratadas", "$tratadas/${linhas.size}", Modifier.weight(1f))
                    Numero("PDV", linhas.map { "${it.local}|${it.pdv}" }.distinct().size.toString(), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Numero("Média por linha tratada", if (tratadas == 0) "—" else euros(totalGeral / tratadas), Modifier.weight(1f))
                    Numero("Valor manual", euros(totalExtras), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("$tratadas de ${linhas.size} linhas tratadas", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${(fracao(tratadas.toLong(), linhas.size.toLong()) * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.padding(top = 6.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                ) {
                    val f = fracao(tratadas.toLong(), linhas.size.toLong())
                    if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary))
                }
            }
        }

        // evolução: uma coluna por quinzena do ano; o período filtrado em destaque, o resto a cinzento
        val anoGrafico = (atual ?: categorias.first()).ano
        val doAno = ordemPeriodos.filter { it.ano == anoGrafico }
        if (doAno.size >= 2) Seccao("Evolução em $anoGrafico") {
            Text("Total por quinzena. Toque numa coluna para ver o valor.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GraficoColunas(
                doAno.map { c -> Coluna("${c.quinzena}ª\n${MESES[c.mes - 1].take(3)}", porPeriodo.getValue(c), c in categorias, c.toString()) },
                ::euros,
            )
        }

        // distribuição das linhas: cada opção com a sua cor fixa (índice nas Opções), valor manual a seguir, por tratar a cinzento
        Seccao("Distribuição das linhas") {
            if (estado.opcoes.isEmpty()) Text(
                "Sem opções definidas. Crie-as em Opções com o valor em euros.",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            val partes = estado.opcoes.mapIndexedNotNull { i, o ->
                val n = linhas.count { !manual(it) && it.opcaoId == o.id }
                val nome = if (parseCentimos(o.valor) != null) "${euros(precoOpcao.getValue(o.id))}" else o.valor
                Parte(nome, n.toLong(), "$n linhas · ${euros(n * precoOpcao.getValue(o.id))}", i).takeIf { n > 0 }
            } + listOfNotNull(
                linhas.count(::manual).takeIf { it > 0 }?.let { Parte("Valor manual", it.toLong(), "$it linhas · ${euros(totalExtras)}", estado.opcoes.size) },
                (linhas.size - tratadas).takeIf { it > 0 }?.let { Parte("Por tratar", it.toLong(), "$it linhas", null) },
            )
            BarraSegmentada(partes)
        }

        val porLocal = linhas.groupBy { it.local }.map { (k, v) -> Triple(k, v, total(v)) }.sortedByDescending { it.third }
        ListaTopo("Por LOCAL", porLocal) { (local, l, t), maior ->
            BarraValor(local.ifEmpty { "(sem local)" }, euros(t), "${l.count(::tratada)} de ${l.size} tratadas", fracao(t, maior))
        }

        val porPdv = linhas.groupBy { it.local to it.pdv }.map { (k, v) -> Triple("${k.second} · ${k.first}", v, total(v)) }.sortedByDescending { it.third }
        ListaTopo("Por PDV", porPdv) { (nome, l, t), maior ->
            BarraValor(nome.ifBlank { "(sem PDV)" }, euros(t), "${l.count(::tratada)} de ${l.size} tratadas", fracao(t, maior))
        }

        if (doPeriodo.size > 1) {
            val porFicheiro = doPeriodo.asReversed().map { Triple(it.nome, it.linhas, total(it.linhas)) }
            ListaTopo("Por ficheiro", porFicheiro) { (nome, l, t), maior ->
                BarraValor(nome, euros(t), "${l.count(::tratada)} de ${l.size} tratadas", fracao(t, maior))
            }
        }
    }
}

/** Secção com os primeiros [TOPO] itens e um botão para ver todos. Barras relativas ao maior valor. */
@Composable
private fun ListaTopo(
    titulo: String,
    itens: List<Triple<String, List<Linha>, Long>>,
    linha: @Composable (Triple<String, List<Linha>, Long>, Long) -> Unit,
) {
    var todos by rememberSaveable(titulo) { mutableStateOf(false) }
    val maior = itens.maxOfOrNull { it.third } ?: 0L
    Seccao(titulo, acao = {
        if (itens.size > TOPO) TextButton(onClick = { todos = !todos }) { Text(if (todos) "Ver menos" else "Ver todos (${itens.size})") }
    }) {
        (if (todos) itens else itens.take(TOPO)).forEach { linha(it, maior) }
    }
}

/** Variação face ao período anterior: ícone + texto (nunca só cor), em verde a subir e vermelho a descer. */
@Composable
private fun Variacao(atual: Long, anterior: Long, contra: String) {
    val diferenca = atual - anterior
    val escuro = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val (icone, cor) = when {
        diferenca > 0 -> R.drawable.ic_subir to Color(if (escuro) 0xFF0CA30C else 0xFF006300)
        diferenca < 0 -> R.drawable.ic_descer to Color(0xFFD03B3B)
        else -> null to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val percentagem = if (anterior > 0) " (${if (diferenca >= 0) "+" else ""}${diferenca * 100 / anterior}%)" else ""
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icone != null) Icone(icone, null, Modifier.size(18.dp), tint = cor)
        Text(
            "${if (diferenca >= 0) "+" else "−"}${euros(kotlin.math.abs(diferenca))}$percentagem $contra",
            Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = cor,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Numero(titulo: String, valor: String, modifier: Modifier) = Column(modifier) {
    Text(titulo, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
}
