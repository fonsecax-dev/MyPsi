package com.aodaci.mypsi

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Ponto(val lat: Double, val lon: Double)

/** Distância em linha reta (haversine). */
fun distanciaKm(a: Ponto, b: Ponto): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * 6371.0 * asin(sqrt(h))
}

/**
 * Índices das paragens pela ordem de visita: vizinho mais próximo a partir da partida
 * (ou da primeira paragem, se não houver partida) + melhoria 2-opt.
 * Paragens com as mesmas coordenadas ficam juntas, pela ordem original.
 */
fun ordenarRota(partida: Ponto?, paragens: List<Ponto>): List<Int> {
    if (paragens.isEmpty()) return emptyList()
    val grupos = paragens.indices.groupBy { paragens[it] }
    val porVisitar = grupos.keys.toMutableList()
    val rota = mutableListOf(partida ?: porVisitar.removeAt(0)) // rota[0] fica fixo
    while (porVisitar.isNotEmpty()) {
        val proximo = porVisitar.minBy { distanciaKm(rota.last(), it) }
        porVisitar.remove(proximo)
        rota.add(proximo)
    }
    var melhorou = true
    while (melhorou) {
        melhorou = false
        for (i in 1 until rota.size - 1) for (j in i + 1 until rota.size) {
            val depois = rota.getOrNull(j + 1)
            val atual = distanciaKm(rota[i - 1], rota[i]) + (depois?.let { distanciaKm(rota[j], it) } ?: 0.0)
            val invertido = distanciaKm(rota[i - 1], rota[j]) + (depois?.let { distanciaKm(rota[i], it) } ?: 0.0)
            if (invertido < atual - 1e-9) {
                rota.subList(i, j + 1).reverse()
                melhorou = true
            }
        }
    }
    return rota.drop(if (partida != null) 1 else 0).flatMap { grupos.getValue(it) }
}

private suspend fun geocodificar(context: Context, consulta: String): Ponto? = withContext(Dispatchers.IO) {
    runCatching {
        @Suppress("DEPRECATION") // a versão com callback só existe a partir da API 33
        Geocoder(context, Locale("pt", "PT")).getFromLocationName(consulta, 1)
            ?.firstOrNull()?.let { Ponto(it.latitude, it.longitude) }
    }.getOrNull()
}

/**
 * Ordem de visita das linhas (colunas LOCAL, PDV, ...), ou null se não for possível
 * localizar alguma localidade (sem internet, geocodificador indisponível).
 */
suspend fun ordemRota(context: Context, linhas: List<List<String>>, moradaPartida: String): List<Int>? {
    val locais = HashMap<String, Ponto>()
    val pdvs = HashMap<Pair<String, String>, Ponto>()
    val pontos = linhas.map { l ->
        val local = locais[l[0]] ?: geocodificar(context, "${l[0]}, Portugal")?.also { locais[l[0]] = it } ?: return null
        pdvs.getOrPut(l[0] to l[1]) {
            // um resultado longe da localidade é um falso positivo do geocodificador: usa a localidade
            geocodificar(context, "Farmácia ${l[1]}, ${l[0]}, Portugal")?.takeIf { distanciaKm(it, local) <= 30 } ?: local
        }
    }
    val partida = moradaPartida.takeIf { it.isNotBlank() }?.let { geocodificar(context, it) }
    return ordenarRota(partida, pontos)
}
