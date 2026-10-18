package com.example.jvcsmilelauncher.util

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.example.jvcsmilelauncher.IShellUserService
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ShizukuExecutor {

    private const val TAG = "ShizukuExecutor"

    data class CommandResult(val success: Boolean, val message: String) {
        override fun toString(): String = "${if (success) "OK" else "FALLO"}: $message"
    }

    @Volatile
    private var service: IShellUserService? = null

    private var latch: CountDownLatch = CountDownLatch(0)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IShellUserService.Stub.asInterface(binder)
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val serviceComponent = ComponentName(
        "com.example.jvcsmilelauncher",
        "com.example.jvcsmilelauncher.shell.ShellUserService"
    )

    private fun obtainService(): IShellUserService {
        service?.let { return it }
        synchronized(this) {
            service?.let { return it }
            val l = CountDownLatch(1)
            latch = l
            val ok = try {
                Shizuku.bindUserService(
                    Shizuku.UserServiceArgs(serviceComponent).version(1),
                    connection
                )
                true
            } catch (t: Throwable) {
                Log.w(TAG, "bindUserService failed: ${t.message}")
                false
            }
            if (!ok || !l.await(10, TimeUnit.SECONDS)) {
                throw IllegalStateException("No se pudo conectar con el servicio Shizuku")
            }
            return service ?: throw IllegalStateException("Servicio Shizuku no disponible")
        }
    }

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        Log.w(TAG, "pingBinder failed: ${t.message}")
        false
    }

    fun exec(command: String): CommandResult = try {
        val svc = obtainService()
        val raw = svc.runShell(command)
        val idx = raw.indexOf('|')
        val code = raw.substring(0, idx).toInt()
        val output = raw.substring(idx + 1).trim()
        CommandResult(code == 0, output.ifEmpty { "exit=$code" })
    } catch (t: Throwable) {
        Log.w(TAG, "exec failed: ${t.message}")
        CommandResult(false, "Error: ${t.message}")
    }
}
