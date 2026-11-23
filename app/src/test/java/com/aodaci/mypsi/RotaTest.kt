package com.aodaci.mypsi

import org.junit.Assert.assertEquals
import org.junit.Test

class RotaTest {
    private val coimbra = Ponto(40.2033, -8.4103)
    private val taveiro = Ponto(40.2000, -8.5100)
    private val lousa = Ponto(40.1100, -8.2460)
    private val arganil = Ponto(40.2180, -8.0540)

    @Test
    fun visitaPorProximidadeAPartirDaPartida() {
        // partida a oeste: Taveiro, Coimbra, Lousã, Arganil
        val ordem = ordenarRota(Ponto(40.15, -8.86), listOf(arganil, coimbra, lousa, taveiro))
        assertEquals(listOf(3, 1, 2, 0), ordem)
    }

    @Test
    fun paragensNoMesmoPontoFicamJuntasPelaOrdemOriginal() {
        val ordem = ordenarRota(Ponto(40.15, -8.86), listOf(coimbra, arganil, coimbra, taveiro, coimbra))
        assertEquals(listOf(3, 0, 2, 4, 1), ordem)
    }

    @Test
    fun agrupaLocaisIguaisPelaPrimeiraOcorrencia() {
        fun l(id: Long, local: String) = Linha(id, local, "", "", "")
        val agrupado = agruparPorLocal(listOf(l(1, "COIMBRA"), l(2, "LOUSÃ"), l(3, "COIMBRA"), l(4, "ARGANIL"), l(5, "LOUSÃ")))
        assertEquals(listOf(1L, 3L, 2L, 5L, 4L), agrupado.map { it.id })
    }

    @Test
    fun opcaoUsadaSoContaLinhasSemValorManual() {
        val estado = Estado(ficheiros = listOf(
            Ficheiro(1, "a.xlsx", 0, listOf(Linha(2, "", "", "", "", opcaoId = 9), Linha(3, "", "", "", "", opcaoId = 9, centimos = 100))),
            Ficheiro(4, "b.xlsx", 0, listOf(Linha(5, "", "", "", "", opcaoId = 8))),
        ))
        assertEquals(mapOf("a.xlsx" to 1), usosOpcao(estado, 9))
        assertEquals(emptyMap<String, Int>(), usosOpcao(estado, 7))
    }

    @Test
    fun categoriaPeloNomeDoFicheiro() {
        assertEquals(Categoria(2026, 11, 2), categoriaDoNome("2ANOV26"))
        assertEquals(Categoria(2026, 4, 1), categoriaDoNome("1ABR26"))
        assertEquals(Categoria(2025, 12, 1), categoriaDoNome("1ª DEZ 2025"))
        assertEquals(Categoria(2027, 1, 2), categoriaDoNome("rota 2-janeiro-27"))
        assertEquals(null, categoriaDoNome("lista farmácias"))
        // sem padrão no nome: usa a data de importação
        val dia20 = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.MARCH, 20, 12, 0) }.timeInMillis
        assertEquals(Categoria(2026, 3, 2), Ficheiro(1, "lista", dia20, emptyList()).categoria)
        val f = Ficheiro(1, "2ANOV26", dia20, emptyList())
        assertEquals(true, noPeriodo(f, 2026, 11, 2))
        assertEquals(false, noPeriodo(f, 2026, 11, 1))
        assertEquals(true, noPeriodo(f, 2026, 11, null))
    }

    @Test
    fun filtraPorAnoEMes() {
        val novembro = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.NOVEMBER, 21, 12, 0) }.timeInMillis
        val f = Ficheiro(1, "a", novembro, emptyList())
        assertEquals(true, noPeriodo(f, 2026, null))
        assertEquals(true, noPeriodo(f, 2026, 11))
        assertEquals(false, noPeriodo(f, 2026, 10))
        assertEquals(false, noPeriodo(f, 2025, null))
    }

    @Test
    fun tratadasVaoParaOFim() {
        fun l(id: Long, local: String, opcao: Long? = null, valor: Long? = null) = Linha(id, local, "", "", "", opcao, valor)
        val (porFazer, feitas) = ordemApresentacao(listOf(l(1, "A"), l(2, "B", opcao = 9), l(3, "A"), l(4, "B"), l(5, "C", valor = 100)))
        assertEquals(listOf(1L, 3L, 4L), porFazer.map { it.id })
        assertEquals(listOf(2L, 5L), feitas.map { it.id })
        // a linha em edição fica no seu grupo até sair do campo
        assertEquals(listOf(1L, 3L, 4L, 5L), ordemApresentacao(listOf(l(1, "A"), l(3, "A"), l(4, "B"), l(5, "C", valor = 100)), exceto = 5).first.map { it.id })
    }

    @Test
    fun ordenaPeloHistorico() {
        fun l(id: Long, local: String, pdv: String, opcao: Long? = null) = Linha(id, local, pdv, "", "", opcao)
        // no ficheiro anterior visitou-se ANTUNES, depois BARROS (tratadas por esta ordem); CENTRAL ficou por tratar
        val anterior = Ficheiro(1, "1ANOV26", 0, listOf(l(2, "COIMBRA", "BARROS"), l(3, "COIMBRA", "CENTRAL"), l(4, "LOUSÃ", "ANTUNES", 9)))
            .let { f -> f.copy(linhas = listOf(f.linhas[1], f.linhas[2], f.linhas[0].copy(opcaoId = 9))) } // ordem guardada: CENTRAL, ANTUNES, BARROS
        val novas = listOf(
            listOf("COIMBRA", "BARROS", "X", ""),
            listOf("COIMBRA", "NOVA", "X", ""),
            listOf("lousã", " antunes ", "X", ""),
            listOf("COIMBRA", "CENTRAL", "X", ""),
        )
        // ANTUNES, BARROS (visitadas), CENTRAL (por tratar), NOVA (sem histórico)
        assertEquals(listOf(2, 0, 3, 1), ordemHistorico(novas, listOf(anterior)))
        assertEquals(listOf(0, 1, 2, 3), ordemHistorico(novas, emptyList()))
    }

    @Test
    fun semPartidaComecaNaPrimeiraParagem() {
        assertEquals(listOf(0, 2, 3, 1), ordenarRota(null, listOf(arganil, taveiro, lousa, coimbra)))
        assertEquals(emptyList<Int>(), ordenarRota(null, emptyList()))
    }
}
