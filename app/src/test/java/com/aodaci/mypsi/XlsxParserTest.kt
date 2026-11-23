package com.aodaci.mypsi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxParserTest {
    // Excel mínimo com os mesmos casos do 2ANOV26.xlsx original: texto formatado (vários <t>),
    // célula unida em LOCAL/PDV, linha vazia de separação e coluna E ignorada.
    private val strings = listOf(
        "<t>LOCAL</t>", "<t>PDV</t>", "<t>PRODUTO</t>", "<t>OBS</t>",
        "<t>COIMBRA</t>", "<t xml:space=\"preserve\"> BARROS</t>",
        "<r><t xml:space=\"preserve\">EXPO </t></r><r><rPr><b/></rPr><t>CHÃO</t></r>",
        "<t>MARCAÇÃO</t>", "<t>VICKS</t>", "<t>LOUSÃ</t>", "<t>ANTUNES</t>",
    )
    private val folha = """
        <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c></row>
        <row r="2"><c r="A2" t="s"><v>4</v></c><c r="B2" t="s"><v>5</v></c><c r="C2" t="s"><v>6</v></c><c r="D2" t="s"><v>7</v></c><c r="E2"><v>99</v></c></row>
        <row r="3"><c r="A3"/><c r="B3"/><c r="C3" t="s"><v>8</v></c><c r="D3"/></row>
        <row r="4"><c r="A4"/><c r="B4"/></row>
        <row r="5"><c r="A5" t="s"><v>9</v></c><c r="B5" t="s"><v>10</v></c><c r="C5" t="inlineStr"><is><t>INLINE</t></is></c><c r="D5"><v>12.5</v></c></row>
    """
    private val linhas = parseXlsx(
        xlsx(
            "xl/sharedStrings.xml" to "<sst xmlns=\"x\">${strings.joinToString("") { "<si>$it</si>" }}</sst>",
            "xl/worksheets/sheet1.xml" to "<worksheet xmlns=\"x\"><sheetData>$folha</sheetData>" +
                "<mergeCells count=\"2\"><mergeCell ref=\"A2:A3\"/><mergeCell ref=\"B2:B3\"/></mergeCells></worksheet>",
        ).inputStream()
    )

    @Test
    fun leColunasSemCabecalhoNemLinhasVazias() {
        assertEquals(
            listOf(
                listOf("COIMBRA", "BARROS", "EXPO CHÃO", "MARCAÇÃO"),
                listOf("COIMBRA", "BARROS", "VICKS", ""), // células unidas repetidas
                listOf("LOUSÃ", "ANTUNES", "INLINE", "12.5"),
            ),
            linhas,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun recusaFicheiroQueNaoEXlsx() {
        parseXlsx(xlsx("outro.txt" to "nada").inputStream())
    }

    @Test
    fun valoresComDuasCasasDecimais() {
        assertEquals(1250L, parseCentimos("12,5"))
        assertEquals(1235L, parseCentimos(" 12.345 "))
        assertEquals(-50L, parseCentimos("-0,5"))
        assertNull(parseCentimos(""))
        assertNull(parseCentimos("-"))
        assertEquals("12,50", formatCentimos(1250))
        assertEquals("-0,05", formatCentimos(-5))
    }

    @Test
    fun nomeSemExtensao() {
        assertEquals("2ANOV26", semExtensao("2ANOV26.xlsx"))
        assertEquals("rota.nov", semExtensao("rota.nov.XLSX"))
        assertEquals("sem extensão", semExtensao("sem extensão"))
    }

    private fun xlsx(vararg partes: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { b ->
        ZipOutputStream(b).use { z -> partes.forEach { (nome, xml) -> z.putNextEntry(ZipEntry(nome)); z.write(xml.toByteArray()) } }
    }.toByteArray()
}
