package com.example.jvcsmilelauncher.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.example.jvcsmilelauncher.R
import com.example.jvcsmilelauncher.util.ShizukuExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThemeEngine {

    enum class Mode { NORMAL, GAMING }

    data class ThemeResult(
        val wallpaperOk: Boolean,
        val results: List<ShizukuExecutor.CommandResult>
    )

    suspend fun applyMode(
        context: Context,
        mode: Mode,
        hz: Int,
        dns: Boolean
    ): ThemeResult {
        val wallpaperOk = withContext(Dispatchers.IO) { applyWallpaper(context, mode) }
        val results = withContext(Dispatchers.IO) { applySystemMode(mode, hz, dns) }
        return ThemeResult(wallpaperOk, results)
    }

    private fun applySystemMode(
        mode: Mode,
        hz: Int,
        dns: Boolean
    ): List<ShizukuExecutor.CommandResult> {
        val out = mutableListOf<ShizukuExecutor.CommandResult>()
        if (!ShizukuExecutor.isAvailable()) {
            out += ShizukuExecutor.CommandResult(false, "Shizuku inactivo: solo se cambió el fondo")
            return out
        }
        val night = if (mode == Mode.GAMING) "yes" else "no"
        out += run("cmd uimode night $night", "Modo oscuro")
        out += run("settings put system peak_refresh_rate $hz", "Refresco ${hz}Hz")
        out += run("settings put system min_refresh_rate $hz", null)
        val anim = if (mode == Mode.GAMING) "0" else "1"
        out += run("settings put global window_animation_scale $anim", "Animaciones")
        out += run("settings put global transition_animation_scale $anim", null)
        out += run("settings put global animator_duration_scale $anim", null)
        if (dns) {
            out += run("settings put global private_dns_mode hostname", "DNS privado")
            out += run("settings put global private_dns_specifier ://cloudflare-dns.com", null)
        } else {
            out += run("settings put global private_dns_mode off", "DNS privado")
        }
        return out
    }

    private fun run(
        command: String,
        label: String?
    ): ShizukuExecutor.CommandResult {
        val r = ShizukuExecutor.exec(command)
        return if (label != null && r.success) {
            ShizukuExecutor.CommandResult(true, label)
        } else {
            r
        }
    }

    private fun applyWallpaper(context: Context, mode: Mode): Boolean = try {
        val wm = WallpaperManager.getInstance(context)
        val metrics = context.resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val top: Int
        val bottom: Int
        if (mode == Mode.GAMING) {
            top = 0xFF1A0006.toInt()
            bottom = 0xFF5C000E.toInt()
        } else {
            top = 0xFF00141E.toInt()
            bottom = 0xFF003A52.toInt()
        }
        val paint = Paint()
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val iconId = if (mode == Mode.GAMING) R.drawable.ic_joker_red else R.drawable.ic_joker_blue
        val icon = BitmapFactory.decodeResource(context.resources, iconId)
        if (icon != null) {
            val target = (minOf(w, h) * 0.62f).toInt()
            val scaled = Bitmap.createScaledBitmap(
                icon,
                target,
                (target * icon.height / icon.width).coerceAtLeast(1),
                true
            )
            canvas.drawBitmap(scaled, (w - scaled.width) / 2f, (h - scaled.height) / 2f, null)
        }
        wm.setBitmap(bmp)
        true
    } catch (t: Throwable) {
        android.util.Log.w("ThemeEngine", "wallpaper failed: ${t.message}")
        false
    }
}
