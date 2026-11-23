package com.aodaci.mypsi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Calendar

val MESES = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
)
private val ABREV = listOf("JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ")

/** Período a que um ficheiro diz respeito. mes 1–12, quinzena 1–2. */
data class Categoria(val ano: Int, val mes: Int, val quinzena: Int) {
    override fun toString() = "${quinzena}ª quinzena ${MESES[mes - 1].take(3)} $ano"
}

// "2ANOV26", "1ª ABR 2026", "2-NOVEMBRO-26": dígito da quinzena, "A"/"ª" opcional, mês abreviado (ou por extenso), ano com 2 ou 4 dígitos
private val PADRAO = Regex(
    """([12])\s*[Aaªº]?\s*[-_ ]?\s*(${ABREV.joinToString("|")})[A-ZÇ]*\s*[-_ ]?\s*(\d{4}|\d{2})(?!\d)""",
    RegexOption.IGNORE_CASE,
)

/** Categoria tirada do nome do ficheiro, ou null se o nome não seguir o padrão. */
fun categoriaDoNome(nome: String): Categoria? {
    val m = PADRAO.find(nome) ?: return null
    val (q, mes, ano) = m.destructured
    return Categoria(ano.toInt().let { if (it < 100) 2000 + it else it }, ABREV.indexOf(mes.uppercase()) + 1, q.toInt())
}

/** Alternativa quando o nome não diz o período: data de importação (dias 1–15 = 1ª quinzena). */
fun categoriaDaData(millis: Long): Categoria = Calendar.getInstance().apply { timeInMillis = millis }.let {
    Categoria(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, if (it.get(Calendar.DAY_OF_MONTH) <= 15) 1 else 2)
}

/** mes == null = ano completo; quinzena == null = mês completo. */
fun noPeriodo(f: Ficheiro, ano: Int, mes: Int?, quinzena: Int? = null): Boolean = f.categoria.let {
    it.ano == ano && (mes == null || it.mes == mes) && (mes == null || quinzena == null || it.quinzena == quinzena)
}

/** Filtro no topo de Dados e Dashboard: ano + (ano completo | mês) + (mês completo | quinzena). */
@Composable
fun FiltroPeriodo(estado: Estado, ano: Int, mes: Int?, quinzena: Int?, mudar: (Int, Int?, Int?) -> Unit) {
    val anos = (estado.ficheiros.map { it.categoria.ano } + categoriaDaData(System.currentTimeMillis()).ano + ano)
        .distinct().sortedDescending()
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Menu(ano.toString(), anos.map { a -> a.toString() to { mudar(a, mes, quinzena) } }, Modifier.weight(1f).padding(end = 8.dp))
            Menu(
                mes?.let { MESES[it - 1] } ?: "Ano completo",
                listOf("Ano completo" to { mudar(ano, null, null) }) + MESES.mapIndexed { i, nome -> nome to { mudar(ano, i + 1, quinzena) } },
                Modifier.weight(1f),
            )
        }
        // a quinzena só aparece depois de escolher um mês (no ano completo não há quinzena)
        if (mes != null) Menu(
            quinzena?.let { "${it}ª quinzena" } ?: "Mês completo",
            listOf("Mês completo" to { mudar(ano, mes, null) }, "1ª quinzena" to { mudar(ano, mes, 1) }, "2ª quinzena" to { mudar(ano, mes, 2) }),
            Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun Menu(texto: String, itens: List<Pair<String, () -> Unit>>, modifier: Modifier) {
    var aberto by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { aberto = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(texto, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icone(R.drawable.ic_expandir, null, tint = androidx.compose.material3.LocalContentColor.current)
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            itens.forEach { (nome, acao) -> DropdownMenuItem(text = { Text(nome) }, onClick = { acao(); aberto = false }) }
        }
    }
}
