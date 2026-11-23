package com.aodaci.mypsi

import android.content.Context
import android.util.AtomicFile
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode

data class Opcao(val id: Long, val valor: String, val visivel: Boolean)

data class Linha(
    val id: Long,
    val local: String,
    val pdv: String,
    val produto: String,
    val obs: String,
    val opcaoId: Long? = null,
    val centimos: Long? = null,
    val nota: String = "", // comentário livre do utilizador
)

data class Ficheiro(
    val id: Long,
    val nome: String,
    val importadoEm: Long,
    val linhas: List<Linha>,
    // ano/mês/quinzena: do nome do ficheiro (ex. "2ANOV26"), senão da data de importação
    val categoria: Categoria = categoriaDoNome(nome) ?: categoriaDaData(importadoEm),
)

data class Estado(
    val opcoes: List<Opcao> = emptyList(),
    val ficheiros: List<Ficheiro> = emptyList(), // por ordem de importação: o último é o mais recente
    val moradaPartida: String = "",
    val letraWidget: Int = 14,
    val corBase: Long = CORES.first().second, // ARGB
    val escalaTexto: Float = 1f,
    val tema: Int = TEMA_SISTEMA,
    val pastaCopia: String = "", // URI da pasta (SAF) da cópia automática; vazio = desligada
    val ultimaCopia: Long = 0,
    val lembrete: Boolean = false, // notificação diária se houver linhas por tratar
    val horaLembrete: Int = 19 * 60, // minutos desde a meia-noite
    val proximoId: Long = 1,
)

val CORES = listOf(
    "Roxo" to 0xFF6750A4, "Azul" to 0xFF1565C0, "Verde" to 0xFF2E7D32, "Turquesa" to 0xFF00796B,
    "Laranja" to 0xFFE65100, "Vermelho" to 0xFFC62828, "Rosa" to 0xFFAD1457, "Cinzento" to 0xFF455A64,
    "Índigo" to 0xFF3949AB, "Azul-mar" to 0xFF0277BD, "Castanho" to 0xFF6D4C41,
)
val LETRAS_WIDGET = listOf(12, 14, 16, 18, 20, 24, 28, 32, 36)
const val TEMA_SISTEMA = 0
const val TEMA_CLARO = 1
const val TEMA_ESCURO = 2
val ESCALA_MIN = 0.8f
val ESCALA_MAX = 1.6f

/** Ficheiros (nome -> n.º de linhas) onde a opção está selecionada; linhas com valor manual não contam (a opção não vale nelas). */
fun usosOpcao(estado: Estado, opcaoId: Long): Map<String, Int> = estado.ficheiros
    .associate { f -> f.nome to f.linhas.count { it.opcaoId == opcaoId && it.centimos == null } }
    .filterValues { it > 0 }

/** "2ANOV26.xlsx" -> "2ANOV26" (só tira extensões de Excel, para não cortar nomes com pontos). */
fun semExtensao(nome: String): String = nome.replace(Regex("\\.(xlsx|xlsm|xls)$", RegexOption.IGNORE_CASE), "")

/** Junta as linhas do mesmo LOCAL, pela ordem em que cada local aparece primeiro; dentro do grupo mantém a ordem. */
fun agruparPorLocal(linhas: List<Linha>): List<Linha> = linhas.groupBy { it.local }.values.flatten()

/**
 * Ordem de importação a partir do histórico: cada linha nova (LOCAL, PDV, ...) fica na posição relativa que o mesmo
 * LOCAL+PDV teve no ficheiro anterior mais recente que o contém (tratadas primeiro, pela ordem em que foram tratadas,
 * que é a ordem real da visita). PDV sem histórico ficam no fim, pela ordem do Excel. Devolve índices de [novas].
 */
fun ordemHistorico(novas: List<List<String>>, historico: List<Ficheiro>): List<Int> {
    fun chave(local: String, pdv: String) = "${local.trim().uppercase()}|${pdv.trim().uppercase()}"
    val posicao = HashMap<String, Double>()
    for (f in historico.asReversed()) { // do mais recente para o mais antigo: o primeiro a definir a chave ganha
        val (porFazer, feitas) = ordemApresentacao(f.linhas)
        val ordem = feitas + porFazer
        ordem.forEachIndexed { i, l -> posicao.putIfAbsent(chave(l.local, l.pdv), i.toDouble() / ordem.size) }
    }
    return novas.indices.sortedWith(compareBy({ posicao[chave(novas[it][0], novas[it][1])] ?: Double.MAX_VALUE }, { it }))
}

/** Acrescenta uma linha escrita à mão (LOCAL, PDV, PRODUTO, OBS) ao fim do ficheiro; fica por tratar, no grupo do seu LOCAL. */
fun Estado.comLinhaNova(ficheiroId: Long, campos: List<String>): Estado = copy(
    proximoId = proximoId + 1,
    ficheiros = ficheiros.map { f ->
        if (f.id != ficheiroId) f else f.copy(linhas = f.linhas + Linha(proximoId, campos[0].trim(), campos[1].trim(), campos[2].trim(), campos[3].trim()))
    },
)

/** Linha com opção escolhida ou valor manual. */
fun tratada(l: Linha) = l.centimos != null || l.opcaoId != null

/**
 * Ordem de apresentação (página do ficheiro e widget): por tratar agrupadas por LOCAL,
 * depois as tratadas pela ordem guardada (= ordem em que foram tratadas). [exceto] fica no sítio (linha em edição).
 */
fun ordemApresentacao(linhas: List<Linha>, exceto: Long? = null): Pair<List<Linha>, List<Linha>> {
    val (feitas, porFazer) = linhas.partition { tratada(it) && it.id != exceto }
    return agruparPorLocal(porFazer) to feitas
}

/** "12,5" ou "12.5" -> 1250; vazio ou inválido -> null */
fun parseCentimos(texto: String): Long? =
    texto.trim().replace(',', '.').toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.movePointRight(2)?.toLong()

fun formatCentimos(centimos: Long): String = BigDecimal.valueOf(centimos, 2).toPlainString().replace('.', ',')

// shortcut: um único JSON regravado por inteiro a cada alteração, chega para dezenas de linhas por ficheiro; trocar por Room se passarem a milhares
object Store {
    private val _estado = MutableStateFlow(Estado())
    val estado: StateFlow<Estado> = _estado

    private var app: Context? = null
    private lateinit var ficheiro: AtomicFile

    @Synchronized
    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        val f = File(context.filesDir, "mypsi.json")
        ficheiro = AtomicFile(f)
        if (!f.exists()) return
        try {
            _estado.value = estadoDeJson(String(ficheiro.readFully()))
        } catch (e: Exception) {
            // não regravar por cima de dados ilegíveis: fica uma cópia para recuperação
            f.copyTo(File(context.filesDir, "mypsi.corrompido.json"), overwrite = true)
        }
    }

    // estado antes da última alteração que se pode desfazer; qualquer outra alteração apaga-o (não se desfaz por cima de outras)
    private var antes: Estado? = null

    @Synchronized
    private fun mudar(desfazivel: Boolean = false, f: (Estado) -> Estado) {
        val atual = _estado.value
        val novo = f(atual)
        if (novo == atual) return
        antes = if (desfazivel) atual else null
        _estado.value = novo
        val out = ficheiro.startWrite()
        try {
            out.write(novo.paraJson().toByteArray())
            ficheiro.finishWrite(out)
        } catch (e: Exception) {
            ficheiro.failWrite(out)
            throw e
        }
        val contexto = app ?: return
        CoroutineScope(Dispatchers.Default).launch { DadosWidget().updateAll(contexto) }
    }

    /** Cria o ficheiro com as linhas (LOCAL, PDV, PRODUTO, OBS) e devolve o seu id. */
    fun importar(nome: String, linhas: List<List<String>>): Long {
        var id = 0L
        mudar { e ->
            id = e.proximoId
            var n = id
            e.copy(
                proximoId = id + linhas.size + 1,
                ficheiros = e.ficheiros + Ficheiro(id, semExtensao(nome), System.currentTimeMillis(), agruparPorLocal(linhas.map { Linha(++n, it[0], it[1], it[2], it[3]) })),
            )
        }
        return id
    }

    fun apagarFicheiro(id: Long) = mudar(desfazivel = true) { e -> e.copy(ficheiros = e.ficheiros.filter { it.id != id }) }

    /** Repõe o estado de antes da última alteração desfazível (apagar ficheiro, escolher opção), se nada mudou entretanto. */
    @Synchronized
    fun desfazer(): Boolean {
        val a = antes ?: return false
        mudar { a }
        return true
    }

    fun adicionarLinha(ficheiroId: Long, campos: List<String>) = mudar { it.comLinhaNova(ficheiroId, campos) }

    /** Corrige LOCAL, PDV, PRODUTO e OBS de uma linha (mantém a opção, o valor e a nota). */
    fun editarLinha(ficheiroId: Long, linhaId: Long, campos: List<String>) = mudar { e ->
        e.comLinha(ficheiroId, linhaId) { it.copy(local = campos[0].trim(), pdv = campos[1].trim(), produto = campos[2].trim(), obs = campos[3].trim()) }
    }

    fun apagarLinha(ficheiroId: Long, linhaId: Long) = mudar(desfazivel = true) { e ->
        e.copy(ficheiros = e.ficheiros.map { f -> if (f.id != ficheiroId) f else f.copy(linhas = f.linhas.filter { it.id != linhaId }) })
    }

    fun definirLembrete(ligado: Boolean, hora: Int) = mudar { it.copy(lembrete = ligado, horaLembrete = hora) }

    fun definirNota(ficheiroId: Long, linhaId: Long, nota: String) = mudar { e -> e.comLinha(ficheiroId, linhaId) { it.copy(nota = nota.trim()) } }

    fun definirPastaCopia(uri: String) = mudar { it.copy(pastaCopia = uri) }

    fun registarCopia(quando: Long) = mudar { it.copy(ultimaCopia = quando) }
    fun reordenar(ficheiroId: Long, idsLinhas: List<Long>) = mudar { e ->
        val posicao = idsLinhas.withIndex().associate { it.value to it.index }
        e.copy(ficheiros = e.ficheiros.map { f ->
            if (f.id != ficheiroId) f else f.copy(linhas = f.linhas.sortedBy { posicao[it.id] ?: Int.MAX_VALUE })
        })
    }

    /** Só uma opção por linha; escolher a que já está selecionada desmarca. Escolher uma opção limpa o valor manual. */
    fun escolherOpcao(ficheiroId: Long, linhaId: Long, opcaoId: Long) = mudar(desfazivel = true) { e ->
        e.comLinha(ficheiroId, linhaId) {
            val nova = opcaoId.takeIf { o -> o != it.opcaoId }
            it.copy(opcaoId = nova, centimos = if (nova != null) null else it.centimos)
        }
    }

    /** O valor manual substitui a opção: escrever um valor tira a seleção. */
    fun definirValor(ficheiroId: Long, linhaId: Long, centimos: Long?) = mudar { e ->
        e.comLinha(ficheiroId, linhaId) { it.copy(centimos = centimos, opcaoId = if (centimos != null) null else it.opcaoId) }
    }

    /** id == null cria uma opção nova. */
    fun guardarOpcao(id: Long?, valor: String, visivel: Boolean) = mudar { e ->
        if (id == null) e.copy(proximoId = e.proximoId + 1, opcoes = e.opcoes + Opcao(e.proximoId, valor, visivel))
        else e.copy(opcoes = e.opcoes.map { if (it.id == id) Opcao(id, valor, visivel) else it })
    }

    /** Só apaga opções que não estejam selecionadas em nenhuma linha (ver [usosOpcao]). */
    fun apagarOpcao(id: Long) = mudar { e ->
        if (usosOpcao(e, id).isNotEmpty()) e
        else e.copy(
            opcoes = e.opcoes.filter { it.id != id },
            ficheiros = e.ficheiros.map { f -> f.copy(linhas = f.linhas.map { if (it.opcaoId == id) it.copy(opcaoId = null) else it }) },
        )
    }

    fun definirMorada(morada: String) = mudar { it.copy(moradaPartida = morada) }

    fun definirCor(argb: Long) = mudar { it.copy(corBase = argb) }

    fun definirEscala(escala: Float) = mudar { it.copy(escalaTexto = escala) }

    fun definirTema(tema: Int) = mudar { it.copy(tema = tema) }

    /** Todos os dados da app em JSON (cópia de segurança). */
    fun exportar(): String = _estado.value.paraJson()

    /** Substitui todos os dados por uma cópia de segurança. Lança exceção se o texto não for uma cópia válida, e nesse caso nada muda. */
    fun importarCopia(texto: String) {
        val novo = estadoDeJson(texto)
        // a pasta da cópia automática é uma permissão deste telemóvel: não vem da cópia
        // a pasta da cópia automática e o lembrete são definições deste telemóvel (permissões, alarme): não vêm da cópia
        mudar { atual -> novo.copy(pastaCopia = atual.pastaCopia, ultimaCopia = atual.ultimaCopia, lembrete = atual.lembrete, horaLembrete = atual.horaLembrete) }
    }

    /** Cicla pelos tamanhos de LETRAS_WIDGET; depois do maior volta ao menor. */
    fun proximaLetraWidget() = mudar { e -> e.copy(letraWidget = LETRAS_WIDGET.firstOrNull { it > e.letraWidget } ?: LETRAS_WIDGET.first()) }

    // uma linha que passa a tratada vai para o fim da lista guardada: as tratadas ficam pela ordem em que foram tratadas
    private fun Estado.comLinha(ficheiroId: Long, linhaId: Long, f: (Linha) -> Linha) = copy(ficheiros = ficheiros.map { fi ->
        if (fi.id != ficheiroId) fi else {
            val antes = fi.linhas.firstOrNull { it.id == linhaId } ?: return@map fi
            val depois = f(antes)
            val outras = fi.linhas.map { if (it.id == linhaId) depois else it }
            fi.copy(linhas = if (!tratada(antes) && tratada(depois)) outras.filter { it.id != linhaId } + depois else outras)
        }
    })
}

internal fun Estado.paraJson(): String = JSONObject()
    .put("proximoId", proximoId)
    .put("moradaPartida", moradaPartida)
    .put("letraWidget", letraWidget)
    .put("corBase", corBase)
    .put("escalaTexto", escalaTexto.toDouble())
    .put("tema", tema)
    .put("pastaCopia", pastaCopia)
    .put("ultimaCopia", ultimaCopia)
    .put("lembrete", lembrete)
    .put("horaLembrete", horaLembrete)
    .put("opcoes", JSONArray(opcoes.map { JSONObject().put("id", it.id).put("valor", it.valor).put("visivel", it.visivel) }))
    .put("ficheiros", JSONArray(ficheiros.map { f ->
        JSONObject().put("id", f.id).put("nome", f.nome).put("importadoEm", f.importadoEm)
            .put("ano", f.categoria.ano).put("mes", f.categoria.mes).put("quinzena", f.categoria.quinzena)
            .put("linhas", JSONArray(f.linhas.map { l ->
                JSONObject().put("id", l.id).put("local", l.local).put("pdv", l.pdv).put("produto", l.produto).put("obs", l.obs)
                    .putOpt("opcaoId", l.opcaoId).putOpt("centimos", l.centimos).putOpt("nota", l.nota.takeIf { it.isNotEmpty() })
            }))
    }))
    .toString()

internal fun estadoDeJson(texto: String): Estado {
    val j = JSONObject(texto)
    return Estado(
        proximoId = j.getLong("proximoId"),
        moradaPartida = j.getString("moradaPartida"),
        letraWidget = j.getInt("letraWidget"),
        // opt*: ficheiros gravados pela versão anterior não têm estes campos
        corBase = j.optLong("corBase", Estado().corBase),
        escalaTexto = j.optDouble("escalaTexto", 1.0).toFloat(),
        tema = j.optInt("tema", TEMA_SISTEMA),
        pastaCopia = j.optString("pastaCopia", ""),
        ultimaCopia = j.optLong("ultimaCopia", 0),
        lembrete = j.optBoolean("lembrete", false),
        horaLembrete = j.optInt("horaLembrete", 19 * 60),
        opcoes = j.getJSONArray("opcoes").objetos().map { Opcao(it.getLong("id"), it.getString("valor"), it.getBoolean("visivel")) },
        ficheiros = j.getJSONArray("ficheiros").objetos().map { f ->
            // semExtensao: ficheiros importados antes desta regra também ficam sem extensão
            Ficheiro(f.getLong("id"), semExtensao(f.getString("nome")), f.getLong("importadoEm"), f.getJSONArray("linhas").objetos().map { l ->
                Linha(
                    l.getLong("id"), l.getString("local"), l.getString("pdv"), l.getString("produto"), l.getString("obs"),
                    if (l.isNull("opcaoId")) null else l.getLong("opcaoId"),
                    if (l.isNull("centimos")) null else l.getLong("centimos"),
                    l.optString("nota", ""),
                )
            }).let { fi ->
                // ficheiros gravados antes da categoria existir ficam com a calculada pelo construtor
                if (f.has("ano")) fi.copy(categoria = Categoria(f.getInt("ano"), f.getInt("mes"), f.getInt("quinzena"))) else fi
            }
        },
    )
}

private fun JSONArray.objetos(): List<JSONObject> = (0 until length()).map(::getJSONObject)
