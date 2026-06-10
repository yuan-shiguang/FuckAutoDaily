package me.teble.xposed.autodaily.su

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.teble.xposed.autodaily.application.xaApp
import me.teble.xposed.autodaily.config.DataMigrationService
import me.teble.xposed.autodaily.utils.TaskExecutor.CORE_SERVICE_FLAG
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object SuApi {
    var isRootAvailable by mutableStateOf(false)
    var isRootGranted by mutableStateOf(false)
    var suType by mutableStateOf(SuType.UNKNOWN)
    private var rootProcess: Process? = null

    enum class SuType {
        UNKNOWN,
        MAGISK,
        KERNELSU,
        SUKISU,
        KERNELSU_NEXT,
        APATCH;

        val displayName: String
            get() = when (this) {
                UNKNOWN -> "Unknown"
                MAGISK -> "Magisk"
                KERNELSU -> "KernelSU"
                SUKISU -> "SukiSU"
                KERNELSU_NEXT -> "KernelSU-Next"
                APATCH -> "APatch"
            }
    }

    fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.waitFor()
            val hasRoot = result?.contains("uid=0") == true
            isRootAvailable = true
            isRootGranted = hasRoot
            if (hasRoot) {
                detectSuType()
            }
            hasRoot
        } catch (e: Exception) {
            Log.e("XALog", "Root check failed", e)
            isRootAvailable = false
            isRootGranted = false
            false
        }
    }

    private fun detectSuType() {
        try {
            val magiskCheck = execShell("which magisk")
            if (magiskCheck.isNotEmpty()) {
                suType = SuType.MAGISK
                return
            }

            val ksuCheck = execShell("which ksud")
            if (ksuCheck.isNotEmpty()) {
                val ksuVersion = execShell("ksud --version")
                if (ksuVersion.contains("SukiSU", ignoreCase = true)) {
                    suType = SuType.SUKISU
                } else if (ksuVersion.contains("Next", ignoreCase = true)) {
                    suType = SuType.KERNELSU_NEXT
                } else {
                    suType = SuType.KERNELSU
                }
                return
            }

            val apatchCheck = execShell("which apd")
            if (apatchCheck.isNotEmpty()) {
                suType = SuType.APATCH
                return
            }

            suType = SuType.UNKNOWN
        } catch (e: Exception) {
            Log.e("XALog", "Su type detection failed", e)
            suType = SuType.UNKNOWN
        }
    }

    fun startService(packageName: String, className: String, args: Array<String>) {
        if (!isRootGranted) return
        val arg = args.joinToString(" ")
        val command =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "start-foreground-service"
            else "startservice"
        suShell("am $command -n $packageName/$className $arg")
    }

    fun setUntrustedTouchEvents(disabled: Boolean) {
        if (!isRootGranted) return
        suShell("settings put global block_untrusted_touches ${if (disabled) 0 else 2}")
    }

    fun execDaemon(packageName: String) {
        if (!isRootGranted) return
        try {
            val conf = SuConfUtil.loadConf()
            if (!conf.enableKeepAlive) {
                Log.d("XALog", "未启用保活，守护进程退出")
                return
            }
            if (conf.alivePackages[packageName] == true) {
                val lockDir = "/sdcard/Android/data/$packageName/files/xa_daemon"
                suShell("mkdir -p $lockDir")
                suShell("chmod 777 $lockDir")
                val lockFile = "$lockDir/.init_service"
                suShell("touch $lockFile")
                suShell("chmod 664 $lockFile")

                while (true) {
                    val isAlive = execShell("pidof $packageName").isNotEmpty()
                    if (!isAlive) {
                        Log.d("XALog", "package: $packageName is died, try start")
                        startService(packageName, DataMigrationService,
                            arrayOf("-e", CORE_SERVICE_FLAG, "$"))
                    }
                    Thread.sleep(10_000)
                }
            }
        } catch (e: Exception) {
            Log.e("XALog", e.stackTraceToString())
        }
    }

    fun suShell(shell: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su -c \"$shell\"")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.e("XALog", "Shell execution failed: $shell", e)
            Toast.makeText(xaApp, "Su shell 执行失败", Toast.LENGTH_SHORT).show()
            ""
        }
    }

    private fun execShell(shell: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su -c \"$shell\"")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readText().also { process.waitFor() }
        } catch (e: Exception) {
            ""
        }
    }
}
