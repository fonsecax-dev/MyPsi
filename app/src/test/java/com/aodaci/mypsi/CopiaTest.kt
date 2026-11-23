package com.aodaci.mypsi

import org.junit.Assert.assertEquals
import org.junit.Test

class CopiaTest {
    @Test
    fun copiaDeSegurancaVoltaIgual() {
        val estado = Estado(
            opcoes = listOf(Opcao(1, "8", true), Opcao(2, "10", false)),
            ficheiros = listOf(
                Ficheiro(
                    3, "2ANOV26", 1795292244192,
                    listOf(Linha(4, "COIMBRA", "BARROS", "EXPO \"G\"", "MARCAÇÃO", opcaoId = 1), Linha(5, "LOUSÃ", "ANTUNES", "X", "", centimos = 250, nota = "voltar na terça")),
                ),
            ),
            moradaPartida = "Rua h",
            letraWidget = 20,
            corBase = CORES[2].second,
            escalaTexto = 1.3f,
            tema = TEMA_ESCURO,
            pastaCopia = "content://pasta",
            ultimaCopia = 1795292244192,
            lembrete = true,
            horaLembrete = 18 * 60 + 30,
            proximoId = 6,
        )
        assertEquals(estado, estadoDeJson(estado.paraJson()))
    }

    @Test
    fun linhaNovaFicaNoFimPorTratarComIdNovo() {
        val e = Estado(proximoId = 10, ficheiros = listOf(Ficheiro(1, "a", 0, listOf(Linha(2, "COIMBRA", "BARROS", "X", "")))))
        val depois = e.comLinhaNova(1, listOf(" LOUSÃ ", "ANTUNES", "VICHY", ""))
        assertEquals(11L, depois.proximoId)
        assertEquals(Linha(10, "LOUSÃ", "ANTUNES", "VICHY", ""), depois.ficheiros[0].linhas.last())
        assertEquals(2, depois.ficheiros[0].linhas.size)
    }

    @Test
    fun textoDoLembrete() {
        fun f(vararg tratadas: Boolean) = Ficheiro(1, "2ANOV26", 0, tratadas.mapIndexed { i, t -> Linha(i.toLong(), "", "", "", "", opcaoId = if (t) 9 else null) })
        assertEquals(null, Lembrete.texto(Estado()))
        assertEquals(null, Lembrete.texto(Estado(ficheiros = listOf(f(true, true)))))
        assertEquals("Falta 1 linha na 2ANOV26", Lembrete.texto(Estado(ficheiros = listOf(f(true, false)))))
        assertEquals("Faltam 3 linhas na 2ANOV26", Lembrete.texto(Estado(ficheiros = listOf(f(false, false, false)))))
    }

    @Test(expected = Exception::class)
    fun recusaFicheiroQueNaoECopia() {
        estadoDeJson("""{"outra":"coisa"}""")
    }
}
