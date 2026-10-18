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

/**
 * Mientras se transmite a la tele, este aviso ("Transmitiendo a Sala") mantiene viva la app: la
 * cola la lleva el celular, y sin un servicio en primer plano Android lo dormiría con la pantalla
 * apagada y la tele se quedaría en la primera canción.
 */
class CastKeepAlive : Service() {
    companion object {
        private const val CHANNEL = "flacker-cast"
        private const val ID = 3333

        /** [name] = a qué aparato se transmite; null = ya no. */
        fun set(context: Context, name: String?) {
            val i = Intent(context, CastKeepAlive::class.java)
            if (name == null) { context.stopService(i); return }
            i.putExtra("name", name)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra("name") ?: "la tele"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Transmitir a la tele", NotificationManager.IMPORTANCE_LOW))
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle("Transmitiendo a $name")
            .setContentText("Flacker lleva la cola desde aquí. Toca para abrir.")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(ID, n)
        return START_NOT_STICKY
    }
}
