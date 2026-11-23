package com.aodaci.mypsi

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Gera um PDF A4 com o resumo do período e abre a partilha do Android. */
object Resumo {
    private const val L = 595 // A4 em pontos
    private const val A = 842
    private const val M = 40f // margem

    fun partilhar(context: Context, periodo: String, ficheiros: List<Ficheiro>, opcoes: List<Opcao>) {
        val pdf = gerar(periodo, ficheiros, opcoes)
        val dir = File(context.cacheDir, "resumos").apply { mkdirs() }
        val f = File(dir, "MyPsi-resumo-${periodo.replace(Regex("[^A-Za-z0-9ºª]+"), "-")}.pdf")
        f.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.ficheiros", f)
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MyPsi · Resumo $periodo")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(envio, "Enviar resumo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun gerar(periodo: String, ficheiros: List<Ficheiro>, opcoes: List<Opcao>): PdfDocument {
        val preco = opcoes.associate { it.id to (parseCentimos(it.valor) ?: 0L) }
        fun total(l: Linha) = l.centimos ?: l.opcaoId?.let { preco[it] } ?: 0L
        fun euros(c: Long) = "${formatCentimos(c)} €"
        val linhas = ficheiros.flatMap { f -> ordemApresentacao(f.linhas).let { (a, b) -> a + b } }

        val doc = PdfDocument()
        val titulo = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val negrito = Paint().apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val normal = Paint().apply { textSize = 10f; isAntiAlias = true }
        val cinza = Paint().apply { textSize = 9f; color = 0xFF666666.toInt(); isAntiAlias = true }
        val linhaP = Paint().apply { color = 0xFFCCCCCC.toInt(); strokeWidth = 0.5f }

        var pagina = doc.startPage(PdfDocument.PageInfo.Builder(L, A, 1).create())
        var c = pagina.canvas
        var y = M + 10
        fun novaPagina() {
            doc.finishPage(pagina)
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(L, A, doc.pages.size + 1).create())
            c = pagina.canvas
            y = M + 10
        }
        fun cabe(altura: Float) { if (y + altura > A - M) novaPagina() }
        fun texto(t: String, x: Float, largura: Float, p: Paint) {
            var s = t
            while (s.isNotEmpty() && p.measureText(s) > largura) s = s.dropLast(1)
            c.drawText(if (s.length < t.length) s.dropLast(1) + "…" else s, x, y, p)
        }

        c.drawText("MyPsi · Resumo $periodo", M, y, titulo); y += 18
        c.drawText("Gerado em ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("pt", "PT")).format(Date())} · ${ficheiros.size} ficheiro(s): ${ficheiros.joinToString(", ") { it.nome }}", M, y, cinza); y += 24

        // totais
        val tratadas = linhas.count(::tratada)
        val manual = linhas.filter { it.centimos != null }
        c.drawText("Total a receber: ${euros(linhas.sumOf(::total))}", M, y, titulo.apply { textSize = 14f }); y += 18
        c.drawText("Linhas tratadas: $tratadas de ${linhas.size}  ·  Por tratar: ${linhas.size - tratadas}", M, y, normal); y += 16
        opcoes.forEach { o ->
            val n = linhas.count { it.centimos == null && it.opcaoId == o.id }
            if (n > 0) { c.drawText("${o.valor}${if (parseCentimos(o.valor) != null) " €" else ""}: $n linha(s) = ${euros(n * preco.getValue(o.id))}", M + 10, y, normal); y += 14 }
        }
        if (manual.isNotEmpty()) { c.drawText("Valor manual: ${manual.size} linha(s) = ${euros(manual.sumOf { it.centimos!! })}", M + 10, y, normal); y += 14 }
        y += 12

        // tabela de linhas
        val colunas = listOf("LOCAL" to 90f, "PDV" to 120f, "PRODUTO" to 165f, "OPÇÃO / VALOR" to 70f, "€" to 70f)
        fun cabecalho() {
            var x = M
            colunas.forEach { (nome, w) -> c.drawText(nome, x, y, negrito); x += w }
            y += 4; c.drawLine(M, y, L - M, y, linhaP); y += 12
        }
        cabe(40f); cabecalho()
        linhas.forEach { l ->
            cabe(16f)
            if (y == M + 10) cabecalho()
            val escolha = when {
                l.centimos != null -> "manual"
                l.opcaoId != null -> opcoes.firstOrNull { it.id == l.opcaoId }?.valor?.let { if (parseCentimos(it) != null) "$it €" else it } ?: "—"
                else -> "por tratar"
            }
            var x = M
            listOf(l.local, l.pdv, l.produto, escolha, if (tratada(l)) euros(total(l)) else "").forEachIndexed { i, t ->
                texto(t, x, colunas[i].second - 6, if (tratada(l)) normal else cinza); x += colunas[i].second
            }
            y += 4; c.drawLine(M, y, L - M, y, linhaP); y += 12
        }
        doc.finishPage(pagina)
        return doc
    }
}
