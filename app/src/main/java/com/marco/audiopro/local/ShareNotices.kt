package com.marco.audiopro.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.marco.audiopro.R

/** Avisos de "te quieren mandar canciones" (suenan) y del envío en curso (callado). */
private const val CHANNEL_ASK = "flacker-share"
private const val CHANNEL_PROGRESS = "flacker-share-progress"

private fun builder(context: Context, title: String, text: String, ask: Boolean): Notification.Builder {
    val channel = if (ask) CHANNEL_ASK else CHANNEL_PROGRESS
    if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        if (ask) NotificationChannel(channel, "Te quieren mandar canciones", NotificationManager.IMPORTANCE_HIGH)
        else NotificationChannel(channel, "Mandando o recibiendo canciones", NotificationManager.IMPORTANCE_LOW),
    )
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    return (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channel) else @Suppress("DEPRECATION") Notification.Builder(context))
        .setSmallIcon(R.drawable.ic_stat_music)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(open)
}

/** "PC-MARCO te quiere mandar 12 canciones": tocarlo abre Flacker, donde se acepta. */
fun notifyIncomingShare(context: Context, text: String) {
    runCatching {
        context.getSystemService(NotificationManager::class.java)
            .notify(3334, builder(context, text, "Toca para abrir Flacker y aceptar", ask = true).setAutoCancel(true).setTimeoutAfter(60_000).build())
    }
}

/**
 * Mientras se mandan o reciben canciones, este aviso mantiene viva la app (si no, Android la
 * duerme con la pantalla apagada y el envío se corta).
 */
class ShareKeepAlive : Service() {
    companion object {
        private const val ID = 3335

        /** [text] = "Mandando canciones a PC-MARCO"; null = ya terminó. */
        fun set(context: Context, text: String?) {
            val i = Intent(context, ShareKeepAlive::class.java)
            if (text == null) { context.stopService(i); return }
            i.putExtra("text", text)
            runCatching { if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = builder(this, intent?.getStringExtra("text") ?: "Mandando canciones", "Flacker · misma red Wi-Fi", ask = false).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        return START_NOT_STICKY
    }
}
