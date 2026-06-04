package com.shuli.reader.core.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder

class TtsService : Service() {

    companion object {
        const val CHANNEL_ID = "tts_playback"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.shuli.reader.tts.PLAY"
        const val ACTION_PAUSE = "com.shuli.reader.tts.PAUSE"
        const val ACTION_STOP = "com.shuli.reader.tts.STOP"

        var onPlay: (() -> Unit)? = null
        var onPause: (() -> Unit)? = null
        var onStop: (() -> Unit)? = null
        var isPlaying: () -> Boolean = { false }
        var currentTitle: () -> String = { "" }
        var currentSubtitle: () -> String = { "" }
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSession(this, "TtsSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { onPlay?.invoke() }
                override fun onPause() { onPause?.invoke() }
                override fun onStop() { onStop?.invoke() }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> onPlay?.invoke()
            ACTION_PAUSE -> onPause?.invoke()
            ACTION_STOP -> {
                onStop?.invoke()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_NOT_STICKY
    }

    fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val title = currentTitle()
        val subtitle = currentSubtitle()
        val playing = isPlaying()

        val playPauseAction = if (playing) {
            Notification.Action.Builder(
                android.R.drawable.ic_media_pause,
                getString(android.R.string.cancel),
                createActionIntent(ACTION_PAUSE),
            ).build()
        } else {
            Notification.Action.Builder(
                android.R.drawable.ic_media_play,
                getString(android.R.string.ok),
                createActionIntent(ACTION_PLAY),
            ).build()
        }

        val stopAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            getString(android.R.string.cancel),
            createActionIntent(ACTION_STOP),
        ).build()

        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    0L, if (playing) 1f else 0f,
                )
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_STOP
                )
                .build()
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title.ifBlank { "ShuLi Reader" })
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(contentPendingIntent)
            .setOngoing(playing)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .addAction(playPauseAction)
            .addAction(stopAction)
            .build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, TtsService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TTS Playback",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Controls for text-to-speech playback"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
