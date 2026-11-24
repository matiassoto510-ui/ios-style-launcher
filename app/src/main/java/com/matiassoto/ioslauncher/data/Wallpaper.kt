package com.matiassoto.ioslauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object WallpaperUtils {
    private fun file(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    /** Original default wallpaper: soft gradient with blurred colour blobs. */
    fun defaultWallpaper(dark: Boolean, w: Int = 720, h: Int = 1560): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val base = if (dark) intArrayOf(0xFF070B1F.toInt(), 0xFF1F1147.toInt(), 0xFF07283F.toInt())
        else intArrayOf(0xFF9CC7FF.toInt(), 0xFFD9C2FF.toInt(), 0xFFFFD2B8.toInt())
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), base, null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        fun blob(cx: Float, cy: Float, r: Float, color: Int) {
            p.shader = RadialGradient(cx, cy, r, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
        }
        if (dark) {
            blob(w * 0.15f, h * 0.22f, w * 0.75f, 0xAA6A3DFF.toInt())
            blob(w * 0.95f, h * 0.55f, w * 0.8f, 0x99FF3D8B.toInt())
            blob(w * 0.30f, h * 0.92f, w * 0.85f, 0x8800B3FF.toInt())
        } else {
            blob(w * 0.10f, h * 0.18f, w * 0.75f, 0xAA5B8CFF.toInt())
            blob(w * 0.95f, h * 0.50f, w * 0.80f, 0x99FF8AC2.toInt())
            blob(w * 0.35f, h * 0.95f, w * 0.85f, 0xAAFFB35C.toInt())
        }
        return bmp
    }

    fun loadCustom(ctx: Context): Bitmap? {
        val f = file(ctx)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }

    fun clearCustom(ctx: Context) { file(ctx).delete() }

    /** Decode the picked image, centre-crop it to the screen aspect ratio, and store it privately. */
    fun importFromUri(ctx: Context, uri: Uri, screenW: Int, screenH: Int): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / (sample * 2) >= screenW && opts.outHeight / (sample * 2) >= screenH) sample *= 2
        val decoded = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val cropped = centerCrop(decoded, screenW.toFloat() / screenH)
        val maxH = 2000
        val out = if (cropped.height > maxH) Bitmap.createScaledBitmap(
            cropped, (cropped.width * maxH.toFloat() / cropped.height).roundToInt(), maxH, true
        ) else cropped
        FileOutputStream(file(ctx)).use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        out
    }.getOrNull()

    private fun centerCrop(src: Bitmap, aspect: Float): Bitmap {
        val srcAspect = src.width.toFloat() / src.height
        return if (srcAspect > aspect) {
            val w = (src.height * aspect).roundToInt()
            Bitmap.createBitmap(src, (src.width - w) / 2, 0, w, src.height)
        } else {
            val h = (src.width / aspect).roundToInt()
            Bitmap.createBitmap(src, 0, (src.height - h) / 2, src.width, h)
        }
    }

    /** Cheap, good-looking blur: downscale + 3-pass box blur. Works on every API level. */
    fun blur(src: Bitmap): Bitmap {
        val w = 90
        val h = max(1, (src.height * w.toFloat() / src.width).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { boxBlur(px, w, h, 4) }
        small.setPixels(px, 0, w, 0, 0, w, h)
        return small
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        val d = 2 * r + 1
        for (y in 0 until h) {
            var ra = 0; var ga = 0; var ba = 0
            for (i in -r..r) {
                val c = px[y * w + i.coerceIn(0, w - 1)]
                ra += c shr 16 and 0xFF; ga += c shr 8 and 0xFF; ba += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[y * w + x] = (0xFF shl 24) or ((ra / d) shl 16) or ((ga / d) shl 8) or (ba / d)
                val o = px[y * w + (x - r).coerceIn(0, w - 1)]
                val n = px[y * w + (x + r + 1).coerceIn(0, w - 1)]
                ra += (n shr 16 and 0xFF) - (o shr 16 and 0xFF)
                ga += (n shr 8 and 0xFF) - (o shr 8 and 0xFF)
                ba += (n and 0xFF) - (o and 0xFF)
            }
        }
        for (x in 0 until w) {
            var ra = 0; var ga = 0; var ba = 0
            for (i in -r..r) {
                val c = tmp[i.coerceIn(0, h - 1) * w + x]
                ra += c shr 16 and 0xFF; ga += c shr 8 and 0xFF; ba += c and 0xFF
            }
            for (y in 0 until h) {
                px[y * w + x] = (0xFF shl 24) or ((ra / d) shl 16) or ((ga / d) shl 8) or (ba / d)
                val o = tmp[(y - r).coerceIn(0, h - 1) * w + x]
                val n = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                ra += (n shr 16 and 0xFF) - (o shr 16 and 0xFF)
                ga += (n shr 8 and 0xFF) - (o shr 8 and 0xFF)
                ba += (n and 0xFF) - (o and 0xFF)
            }
        }
    }
}
