package com.example.jvcsmilelauncher.util

import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuExecutor {

    data class CommandResult(val success: Boolean, val message: String) {
        override fun toString(): String = "${if (success) "OK" else "FALLO"}: $message"
    }

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        Log.w("ShizukuExecutor", "pingBinder failed: ${t.message}")
        false
    }

    fun exec(command: String): CommandResult = try {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        val output = (stdout + stderr).trim()
        CommandResult(exitCode == 0, output.ifEmpty { "exit=$exitCode" })
    } catch (t: Throwable) {
        Log.w("ShizukuExecutor", "exec failed: ${t.message}")
        CommandResult(false, "Error: ${t.message}")
    }
}
