package com.aodaci.mypsi

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

/** Lembrete diário: à hora escolhida, notifica se o último ficheiro ainda tiver linhas por tratar. */
object Lembrete {
    private const val CANAL = "lembretes"
    private const val ACAO = "com.aodaci.mypsi.LEMBRETE"

    private fun intencao(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, LembreteReceiver::class.java).setAction(ACAO),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Agenda o próximo lembrete (ou cancela, se estiver desligado). Chamado ao abrir a app, ao mudar a definição e após reiniciar o telemóvel. */
    fun agendar(context: Context) {
        Store.init(context)
        val e = Store.estado.value
        val alarmes = context.getSystemService(AlarmManager::class.java)
        if (!e.lembrete) { alarmes.cancel(intencao(context)); return }
        val proximo = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, e.horaLembrete / 60); set(Calendar.MINUTE, e.horaLembrete % 60); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        // janela de 10 min: não precisa da permissão de alarmes exatos e chega para um lembrete
        alarmes.setWindow(AlarmManager.RTC_WAKEUP, proximo.timeInMillis, 10 * 60 * 1000L, intencao(context))
    }

    /** Texto do lembrete, ou null se não houver nada por tratar. */
    fun texto(estado: Estado): String? {
        val f = estado.ficheiros.lastOrNull() ?: return null
        val faltam = f.linhas.count { !tratada(it) }
        return if (faltam == 0) null else "Falta${if (faltam > 1) "m" else ""} $faltam linha${if (faltam > 1) "s" else ""} na ${f.nome}"
    }

    fun notificar(context: Context) {
        Store.init(context)
        val texto = texto(Store.estado.value) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val gestor = context.getSystemService(NotificationManager::class.java)
        gestor.createNotificationChannel(NotificationChannel(CANAL, "Lembretes", NotificationManager.IMPORTANCE_DEFAULT))
        val abrir = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle("MyPsi")
            .setContentText(texto)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, n)
    }
}

/** Recebe o alarme diário (e o reinício do telemóvel, para voltar a agendar). */
class LembreteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.aodaci.mypsi.LEMBRETE") Lembrete.notificar(context)
        Lembrete.agendar(context) // o alarme é de uma só vez: agenda o de amanhã
    }
}
