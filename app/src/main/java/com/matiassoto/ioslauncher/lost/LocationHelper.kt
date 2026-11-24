package com.matiassoto.ioslauncher.lost

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

object LocationHelper {
    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Best effort: waits up to [timeoutMs] for a GPS fix, falls back to network / last known. */
    @SuppressLint("MissingPermission")
    fun getLocation(ctx: Context, timeoutMs: Long = 25_000, cb: (Location?) -> Unit) {
        if (!hasPermission(ctx)) { cb(null); return }
        val lm = ctx.getSystemService(LocationManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val lastKnown = (providers + LocationManager.PASSIVE_PROVIDER).mapNotNull {
            runCatching { lm.getLastKnownLocation(it) }.getOrNull()
        }.maxByOrNull { it.time }
        if (providers.isEmpty()) { cb(lastKnown); return }

        var done = false
        var best: Location? = null
        val signals = mutableListOf<CancellationSignal>()
        val listeners = mutableListOf<LocationListener>()
        fun finish(loc: Location?) {
            if (done) return
            done = true
            signals.forEach { it.cancel() }
            listeners.forEach { runCatching { lm.removeUpdates(it) } }
            cb(loc ?: best ?: lastKnown)
        }
        fun onFix(loc: Location?) {
            if (loc == null || done) return
            if (best == null || loc.accuracy < best!!.accuracy) best = loc
            if (loc.provider == LocationManager.GPS_PROVIDER || loc.accuracy <= 30f) finish(loc)
        }
        handler.postDelayed({ finish(null) }, timeoutMs)
        for (p in providers) {
            if (Build.VERSION.SDK_INT >= 30) {
                val s = CancellationSignal(); signals += s
                runCatching { lm.getCurrentLocation(p, s, ContextCompat.getMainExecutor(ctx)) { onFix(it) } }
            } else {
                val l = object : LocationListener {
                    override fun onLocationChanged(location: Location) = onFix(location)
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                listeners += l
                @Suppress("DEPRECATION")
                runCatching { lm.requestSingleUpdate(p, l, Looper.getMainLooper()) }
            }
        }
    }
}
