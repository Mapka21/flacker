package com.marco.audiopro.download

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.marco.audiopro.MainActivity
import com.marco.audiopro.R
import com.marco.audiopro.container

class MarcoDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.downloads_channel,
    0,
) {
    private val helper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = container.downloads.manager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val left = downloads.count { it.state != Download.STATE_COMPLETED }
        return helper.buildProgressNotification(
            this, R.drawable.ic_stat_music, open,
            if (notMetRequirements != 0) "Esperando Wi-Fi…" else "Descargando $left canciones",
            downloads, notMetRequirements,
        )
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        private const val CHANNEL_ID = "downloads"
    }
}
