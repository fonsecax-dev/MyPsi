package com.aodaci.mypsi

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.time.LocalDate
import kotlin.concurrent.thread

/** Cópia de segurança automática para uma pasta escolhida pelo utilizador (Storage Access Framework). */
object CopiaAutomatica {
    private const val INTERVALO = 24 * 60 * 60 * 1000L // no máximo uma cópia por dia

    /** Chamado ao abrir a app: grava uma cópia se a função estiver ligada e a última tiver mais de um dia. */
    fun talvezFazer(context: Context) {
        val e = Store.estado.value
        if (e.pastaCopia.isBlank() || System.currentTimeMillis() - e.ultimaCopia < INTERVALO) return
        fazerAgora(context.applicationContext, e.pastaCopia)
    }

    // shortcut: um ficheiro por dia de uso, sem apagar os antigos (~10 KB cada); limpar os mais velhos se a pasta crescer demasiado
    fun fazerAgora(context: Context, pasta: String, depois: (Boolean) -> Unit = {}) = thread {
        val ok = runCatching {
            val arvore = Uri.parse(pasta)
            val dir = DocumentsContract.buildDocumentUriUsingTree(arvore, DocumentsContract.getTreeDocumentId(arvore))
            val doc = DocumentsContract.createDocument(context.contentResolver, dir, "application/json", "MyPsi-copia-auto-${LocalDate.now()}.json")
                ?: error("não foi possível criar o ficheiro")
            context.contentResolver.openOutputStream(doc)!!.use { it.write(Store.exportar().toByteArray()) }
        }.isSuccess
        if (ok) Store.registarCopia(System.currentTimeMillis())
        depois(ok)
    }
}
