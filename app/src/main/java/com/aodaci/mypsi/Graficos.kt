package com.aodaci.mypsi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// paleta categórica validada (dataviz, referência): a ordem é o mecanismo de segurança para daltonismo — não reordenar
private val SERIES_CLARO = listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF6250D6, 0xFFE34948)
private val SERIES_ESCURO = listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF0CA30C, 0xFF9085E9, 0xFFE66767)

/** Cor da série n (fixa por entidade, nunca pela posição no ranking). */
@Composable
fun corSerie(n: Int): Color {
    val escuro = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // shortcut: mais de 8 séries partilham a última cor; a paleta não admite cores geradas — agrupar em "Outras" se aparecer
    return Color((if (escuro) SERIES_ESCURO else SERIES_CLARO)[n.coerceIn(0, 7)])
}

/** Texto dentro de um bloco colorido: branco, exceto preto sobre o amarelo (slot 4). */
private fun tintaSobre(n: Int) = if (n == 3) Color(0xFF0B0B0B) else Color.White

/** Parte de um todo: nome, quantidade (define o tamanho), texto de detalhe e cor (null = cinzento "por tratar"). */
data class Parte(val nome: String, val quantidade: Long, val detalhe: String, val serie: Int?)

/** Barra segmentada alta (≤ 6 partes) + legenda com nomes e valores; a percentagem vai no bloco quando cabe. */
@Composable
fun BarraSegmentada(partes: List<Parte>) {
    val total = partes.sumOf { it.quantidade }.takeIf { it > 0 } ?: return
    val visiveis = partes.filter { it.quantidade > 0 }
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().height(32.dp)
            .semantics { contentDescription = partes.joinToString("; ") { "${it.nome}: ${it.detalhe}" } },
        horizontalArrangement = Arrangement.spacedBy(2.dp), // 2dp de superfície entre blocos
    ) {
        visiveis.forEachIndexed { i, p ->
            val fracao = p.quantidade.toFloat() / total
            val cantos = RoundedCornerShape(
                topStart = if (i == 0) 6.dp else 0.dp, bottomStart = if (i == 0) 6.dp else 0.dp,
                topEnd = if (i == visiveis.lastIndex) 6.dp else 0.dp, bottomEnd = if (i == visiveis.lastIndex) 6.dp else 0.dp,
            )
            val cor = p.serie?.let { corSerie(it) } ?: MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.weight(fracao).fillMaxHeight().clip(cantos).background(cor), contentAlignment = Alignment.Center) {
                if (fracao >= 0.12f) Text(
                    "${(fracao * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = p.serie?.let(::tintaSobre) ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
    // legenda: identidade nunca só pela cor — nome e valores em texto
    Column(Modifier.padding(top = 8.dp)) {
        partes.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(p.serie?.let { corSerie(it) } ?: MaterialTheme.colorScheme.outlineVariant))
                Text(p.nome, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(p.detalhe, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Ponto de um gráfico de colunas: rótulo curto, valor (cêntimos), destacado ou não (ênfase: o resto fica cinzento). */
data class Coluna(val rotulo: String, val valor: Long, val destaque: Boolean, val detalhe: String)

/** Gráfico de colunas de uma série; tocar numa coluna mostra o valor dela. */
@Composable
fun GraficoColunas(colunas: List<Coluna>, formatar: (Long) -> String, alturaGrafico: Int = 140) {
    val maior = colunas.maxOfOrNull { it.valor }?.takeIf { it > 0 } ?: 1L
    // por omissão mostra a última coluna destacada (o período mais recente do filtro)
    var escolhida by remember(colunas) { mutableStateOf(colunas.indexOfLast { it.destaque }.takeIf { it >= 0 } ?: colunas.lastIndex) }
    val sel = colunas.getOrNull(escolhida)
    if (sel != null) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(sel.detalhe, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatar(sel.valor), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(alturaGrafico.dp), verticalAlignment = Alignment.Bottom) {
        colunas.forEachIndexed { i, c ->
            val cor = when {
                i == escolhida -> MaterialTheme.colorScheme.primary
                c.destaque -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClickLabel = "Ver valor") { escolhida = i } // alvo de toque = coluna inteira, maior que a barra
                    .semantics { contentDescription = "${c.detalhe}: ${formatar(c.valor)}" }
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val fracao = c.valor.toFloat() / maior
                if (fracao > 0f) Box(
                    Modifier.fillMaxWidth().fillMaxHeight(fracao.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(cor),
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline) // linha de base
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        colunas.forEachIndexed { i, c ->
            Text(
                c.rotulo,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (i == escolhida) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (i == escolhida) FontWeight.Bold else null,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Clip,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}
