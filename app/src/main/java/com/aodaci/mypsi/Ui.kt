package com.aodaci.mypsi

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ícone vetorial da app (res/drawable). description == null para ícones decorativos. */
@Composable
fun Icone(@DrawableRes id: Int, description: String?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Icon(painterResource(id), contentDescription = description, modifier = modifier, tint = tint)

/** Logótipo da app: as mesmas camadas do ícone do launcher (gradiente + Ψ). */
@Composable
fun Logotipo(tamanho: Dp = 40.dp) = Box(
    Modifier
        .size(tamanho)
        .clip(CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFF5B3FD6), Color(0xFF1E88E5)))),
) {
    // o desenho do launcher ocupa ~60% da tela de 108dp; ampliar para preencher o círculo
    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize().scale(1.5f))
}

/** Mostra um aviso com o botão "Desfazer" (repõe a última alteração desfazível); fornecido pela App. */
val LocalDesfazer = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Abre a página de Ajuda; fornecido pela App, para não passar o callback por todos os ecrãs. */
val LocalAbrirAjuda = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Barra de topo dos ecrãs principais: logótipo + título + ajuda. */
@Composable
fun BarraTopo(titulo: String) = Row(
    Modifier.fillMaxWidth().padding(bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Logotipo()
    Text(titulo, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    LocalAbrirAjuda.current?.let { abrir ->
        IconButton(onClick = abrir) { Icone(R.drawable.ic_ajuda, "Ajuda", Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) }
    }
}

/** Estado vazio: ícone, título e o que fazer a seguir. */
@Composable
fun EstadoVazio(titulo: String, texto: String, modifier: Modifier = Modifier) = Column(
    modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Icone(R.drawable.ic_pasta, null, Modifier.size(56.dp))
    Text(titulo, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Text(
        texto,
        Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Linha de dashboard: título e valor à direita, barra simples, detalhe por baixo. */
@Composable
fun BarraValor(titulo: String, valor: String, detalhe: String, fracao: Float) = Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (fracao > 0f) Box(
            Modifier
                .fillMaxWidth(fracao.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
    if (detalhe.isNotEmpty()) Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Cartão de secção com título (Dashboard e Opções). */
@Composable
fun Seccao(titulo: String, modifier: Modifier = Modifier, acao: @Composable () -> Unit = {}, conteudo: @Composable () -> Unit) =
    Card(modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                acao()
            }
            conteudo()
        }
    }
