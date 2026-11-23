package com.aodaci.mypsi

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

private const val COLUNAS = 4

/**
 * Lê as colunas A–D da primeira folha de um .xlsx: sem cabeçalho, sem linhas vazias,
 * com as células unidas repetidas em cada linha.
 */
fun parseXlsx(input: InputStream): List<List<String>> {
    val partes = HashMap<String, ByteArray>()
    ZipInputStream(input).use { zip ->
        generateSequence { zip.nextEntry }.forEach {
            if (it.name == "xl/sharedStrings.xml" || it.name.startsWith("xl/worksheets/sheet")) partes[it.name] = zip.readBytes()
        }
    }
    // shortcut: assume que a primeira folha é a de nome mais baixo (sheet1.xml); ler xl/workbook.xml se houver ficheiros com folhas reordenadas
    val folha = partes.keys.filter { it.startsWith("xl/worksheets/") }.minOrNull()
        ?: throw IllegalArgumentException("Não é um ficheiro .xlsx")

    val textos = partes["xl/sharedStrings.xml"]?.let { xml ->
        dom(xml).elementos("si").map { si -> si.elementos("t").joinToString("") { it.textContent } }
    } ?: emptyList()

    val doc = dom(partes.getValue(folha))
    val linhas = sortedMapOf<Int, Array<String>>()
    for (c in doc.elementos("c")) {
        val (col, lin) = posicao(c.getAttribute("r")) ?: continue
        if (col >= COLUNAS) continue
        val v = c.elementos("v").firstOrNull()?.textContent
        val valor = when (c.getAttribute("t")) {
            "s" -> v?.toIntOrNull()?.let(textos::getOrNull) ?: ""
            "inlineStr" -> c.elementos("t").joinToString("") { it.textContent }
            else -> v ?: ""
        }
        linhas.getOrPut(lin) { Array(COLUNAS) { "" } }[col] = valor
    }
    for (m in doc.elementos("mergeCell")) {
        val (de, ate) = m.getAttribute("ref").split(":").mapNotNull(::posicao).takeIf { it.size == 2 } ?: continue
        val valor = linhas[de.second]?.getOrNull(de.first) ?: continue
        for ((lin, celulas) in linhas.subMap(de.second, ate.second + 1))
            for (col in de.first..minOf(ate.first, COLUNAS - 1)) celulas[col] = valor
    }
    return linhas.values.drop(1) // a primeira linha é o cabeçalho
        .map { l -> l.map { it.trim() } }
        .filter { l -> l.any { it.isNotEmpty() } }
}

/** "B12" -> (1, 12) */
private fun posicao(ref: String): Pair<Int, Int>? {
    val letras = ref.takeWhile { it.isLetter() }
    val linha = ref.drop(letras.length).toIntOrNull() ?: return null
    if (letras.isEmpty()) return null
    return letras.fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1 to linha
}

private fun dom(xml: ByteArray): Element {
    val fabrica = DocumentBuilderFactory.newInstance()
    // ficheiro escolhido pelo utilizador: não aceitar DOCTYPE/entidades externas
    runCatching { fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    fabrica.isExpandEntityReferences = false
    return fabrica.newDocumentBuilder().parse(ByteArrayInputStream(xml)).documentElement
}

private fun Element.elementos(tag: String): List<Element> =
    getElementsByTagName(tag).let { n -> (0 until n.length).map { n.item(it) as Element } }
