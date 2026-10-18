package com.example.jvcsmilelauncher.util

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuPermission {

    private const val REQUEST_CODE = 1001

    fun permissionGranted(): Boolean = try {
        Shizuku.pingBinder() &&
            (Shizuku.isPreV11() || Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
    } catch (t: Throwable) {
        false
    }

    fun ensureShizukuAvailable(context: Context) {
        if (!Shizuku.pingBinder()) {
            throw IllegalStateException("Shizuku no está activo. Abre Shizuku Manager y actívalo.")
        }
        if (!Shizuku.isPreV11() && Shizuku.getVersion() < 11) {
            throw IllegalStateException("Shizuku está desactualizado. Actualiza Shizuku Manager.")
        }
        if (!permissionGranted()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }
}
