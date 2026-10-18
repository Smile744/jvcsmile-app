package com.example.jvcsmilelauncher.shell

import com.example.jvcsmilelauncher.IShellUserService

class ShellUserService : IShellUserService.Stub() {
    override fun runShell(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val code = process.waitFor()
            "$code|${stdout + stderr}"
        } catch (t: Throwable) {
            "1|${t.message}"
        }
    }

    override fun destroy() {}
}
