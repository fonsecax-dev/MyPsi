package com.aodaci.mypsi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Página de ajuda: tudo o que a app faz, por secções. */
@Composable
fun AjudaScreen(voltar: () -> Unit) {
    BackHandler(onBack = voltar)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = voltar, modifier = Modifier.size(56.dp)) {
                Icone(R.drawable.ic_voltar, "Voltar", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("Ajuda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Seccao("O que é a MyPsi") {
            P("A MyPsi serve para registar, PDV a PDV, quanto se recebe em cada linha das listas de rota que chegam em Excel, e ver os totais por quinzena, mês ou ano.")
            P("Tem três separadores em baixo: Dados (os ficheiros e as linhas), Dashboard (totais e gráficos) e Opções (definições). Há também um widget para o ecrã inicial.")
            P("Todos os dados ficam guardados só neste telemóvel. Faça cópias de segurança regularmente (ver Opções → Cópia de segurança).")
        }

        Seccao("Importar um ficheiro Excel") {
            P("Em Dados, toque em Importar (botão no canto inferior direito) e escolha a forma de ordenar. Também pode abrir o Excel diretamente a partir do email, do WhatsApp ou dos Ficheiros: toque no anexo e escolha \"MyPsi\" (Abrir com ou Partilhar).")
            Item("Com a ordenação do ficheiro: as linhas ficam pela ordem do Excel.")
            Item("Ordenar a rota automaticamente: a app procura cada farmácia no mapa (precisa de internet) e ordena a rota a partir da morada de partida definida nas Opções. Se não conseguir localizar, importa pela ordem do ficheiro e avisa.")
            Item("Ordenar pelo histórico dos ficheiros: cada PDV fica na posição em que foi visitado no ficheiro anterior mais recente que o tinha. PDV novos ficam no fim.")
            P("O Excel deve ter na primeira folha as colunas LOCAL, PDV, PRODUTO e OBS, com o cabeçalho na primeira linha. Células unidas são repetidas em cada linha e linhas vazias são ignoradas. Só são aceites ficheiros .xlsx.")
            P("O nome do ficheiro indica o período: por exemplo 2ANOV26 = 2.ª quinzena de novembro de 2026 (também aceita 1ABR26, \"1ª DEZ 2025\", \"2-janeiro-27\"). Se o nome não tiver este padrão, usa a data de importação. A extensão .xlsx é retirada do nome.")
        }

        Seccao("Lista de ficheiros") {
            Item("No topo há o filtro de período: ano, depois ano completo ou um mês e, escolhido um mês, mês completo ou uma quinzena. O mesmo filtro aplica-se ao Dashboard.")
            Item("Toque num ficheiro para o abrir.")
            Item("O ícone vermelho do caixote do lixo apaga o ficheiro (pede confirmação). Logo a seguir aparece \"Desfazer\" durante alguns segundos.")
            Item("Ao abrir a app, entra-se diretamente no último ficheiro se ainda tiver linhas por tratar.")
        }

        Seccao("Página do ficheiro") {
            Item("As linhas por tratar aparecem agrupadas por LOCAL. Cada linha mostra o PDV, o produto e a OBS (a cor).")
            Item("Em cada linha escolha uma opção (as colunas com o valor em euros) ou escreva um valor no campo VALOR. Só pode haver uma das duas: escrever um valor tira a opção e escolher uma opção limpa o valor. Tocar na opção já escolhida desmarca-a.")
            Item("Quando uma linha recebe uma opção ou um valor passa para o fim do ecrã, na secção Tratadas, pela ordem em que foi tratada. Se tirar a seleção, volta ao seu LOCAL. Enquanto escreve no VALOR a linha só muda de sítio quando sai do campo.")
            Item("Arraste a pega (⋮⋮) à esquerda de uma linha para a mudar de posição dentro do grupo, ou a pega de um cabeçalho para mudar a ordem dos LOCAIS.")
            Item("Ao escolher uma opção o telemóvel vibra e aparece \"Desfazer\" durante alguns segundos, para corrigir um toque por engano.")
            Item("Toque no nome de uma linha para abrir o menu: \"Navegar até ao PDV\" (abre o Google Maps com o caminho até à farmácia) e \"Escrever nota\" (um comentário livre, que aparece por baixo da linha e no widget).")
            Item("O botão + no topo acrescenta uma linha à mão (ex.: um PDV visitado que não vinha na lista). No menu de cada linha há também \"Editar linha\" para corrigir LOCAL, PDV, produto e OBS, e \"Apagar linha\" (com Desfazer).")
            Item("A lupa no topo pesquisa por PDV, LOCAL, produto, OBS ou nota (sem precisar de acentos). Durante a pesquisa não é possível arrastar linhas.")
            Item("Toque num cabeçalho para recolher ou expandir esse grupo; o botão no canto superior direito recolhe ou expande todos.")
            Item("A seta no canto superior esquerdo volta à lista de ficheiros.")
        }

        Seccao("Dashboard") {
            Item("\"Enviar resumo (PDF)\" gera um PDF do período do filtro (totais, distribuição e todas as linhas) e abre a partilha: email, WhatsApp, Drive…")
            Item("Total a receber do período do filtro, com a variação face à quinzena anterior quando o filtro mostra uma única quinzena (▲ verde a subir, ▼ vermelho a descer).")
            Item("Números-chave: linhas tratadas, número de PDV, média por linha tratada e valor manual, e a percentagem de linhas tratadas.")
            Item("Evolução: uma coluna por quinzena do ano. O período do filtro fica na cor da app; toque numa coluna para ver o valor.")
            Item("Distribuição das linhas: quanto pesa cada opção, o valor manual e as linhas por tratar, com linhas e euros de cada parte.")
            Item("Por LOCAL, Por PDV e Por ficheiro: os que mais rendem, com \"Ver todos\" para a lista completa.")
            P("Cada linha conta pelo valor da opção escolhida ou, se tiver, pelo valor manual. Opções cujo nome não seja um número contam como 0 €.")
        }

        Seccao("Opções") {
            Item("Aparência: tema (sistema, claro ou escuro), cor base (11 cores ou o círculo arco-íris para escolher qualquer cor) e tamanho do texto.")
            Item("Rota: morada de partida usada na ordenação automática da rota.")
            Item("Colunas de opção: os valores em euros que aparecem como colunas em cada linha. Adicione, edite (toque na opção), oculte (interruptor Visível) ou apague (caixote do lixo). Uma opção que já esteja escolhida em alguma linha não pode ser apagada; pode ocultá-la.")
            Item("Lembrete: ligue o lembrete diário e escolha a hora. A essa hora, se o último ficheiro ainda tiver linhas por tratar, aparece uma notificação (ex.: \"Faltam 4 linhas na 2ANOV26\"); tocar nela abre a app.")
            Item("Cópia de segurança: Exportar grava todos os dados num ficheiro .json (por exemplo no Drive); Importar substitui todos os dados atuais pelos de uma cópia.")
            Item("Cópia automática diária: ligue o interruptor e escolha uma pasta. A partir daí, em cada dia em que abrir a app, é gravada uma cópia nessa pasta (MyPsi-copia-auto-data.json).")
        }

        Seccao("Widget") {
            P("Toque longo no ecrã inicial → Widgets → MyPsi. O widget mostra o último ficheiro importado, com a mesma ordem da app (por tratar agrupadas por LOCAL, tratadas no fim).")
            Item("Escolha uma opção diretamente no widget.")
            Item("Toque no nome de uma linha para abrir a app nessa linha, com o cursor no VALOR.")
            Item("Toque no logótipo ou no nome do ficheiro para abrir a app.")
            Item("O botão redondo no canto superior direito aumenta a letra; depois do maior tamanho volta ao menor.")
        }

        Seccao("Dicas") {
            Item("Faça uma cópia de segurança antes de mudar de telemóvel ou de desinstalar a app: desinstalar apaga todos os dados.")
            Item("Se a ordenação automática da rota falhar, verifique a ligação à internet e a morada de partida.")
            Item("Para corrigir o período de um ficheiro, mude o nome do Excel (ex. 1ADEZ26) e volte a importá-lo.")
        }

        Text(
            "Desenvolvido para Susana Baltazar por Sérgio Fonseca",
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun P(texto: String) = Text(texto, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)

@Composable
private fun Item(texto: String) = Row(Modifier.padding(top = 8.dp)) {
    Text("•", Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    Text(texto, style = MaterialTheme.typography.bodyMedium)
}
