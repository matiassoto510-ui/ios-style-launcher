package com.matiassoto.ioslauncher.lost

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Loud alarm on the ALARM stream at max volume (plays through silent/vibrate mode) for 60 s. */
object AlarmPlayer {
    const val DURATION_MS = 60_000L
    private var player: MediaPlayer? = null
    private var savedVolume: Int = -1
    private val handler = Handler(Looper.getMainLooper())
    private val _ringing = MutableStateFlow(false)
    val ringing: StateFlow<Boolean> = _ringing
    private var stopListener: (() -> Unit)? = null

    fun start(context: Context, onStopped: (() -> Unit)? = null) {
        val ctx = context.applicationContext
        stopListener = onStopped
        if (player != null) return
        val am = ctx.getSystemService(AudioManager::class.java)
        savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
        val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(ctx, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        vibrator(ctx)?.let { v ->
            runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400), 0)) }
        }
        _ringing.value = true
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stop(ctx) }, DURATION_MS)
    }

    fun stop(context: Context) {
        val ctx = context.applicationContext
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator(ctx)?.cancel()
        if (savedVolume >= 0) {
            runCatching { ctx.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0) }
            savedVolume = -1
        }
        if (_ringing.value) {
            _ringing.value = false
            stopListener?.invoke()
        }
        stopListener = null
    }

    private fun vibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
}
