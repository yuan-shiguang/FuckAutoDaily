package me.teble.xposed.autodaily.su

import android.util.Log
import me.teble.xposed.autodaily.application.xaApp
import me.teble.xposed.autodaily.utils.parse
import me.teble.xposed.autodaily.utils.toJsonString
import java.io.File

object SuConfUtil {
    private val sdcardPath = "/sdcard"

    fun loadConf(): SuConf {
        val confDir = File(sdcardPath, "Android/data/${xaApp.packageName}/files/conf")
        if (confDir.isFile) {
            confDir.delete()
        }
        if (!confDir.exists()) {
            confDir.mkdirs()
        }
        val confFile = File(confDir, "conf.json")
        runCatching {
            return confFile.readText().parse()
        }
        return SuConf(false, HashMap())
    }

    fun saveConf(conf: SuConf) {
        val confDir = File(sdcardPath, "Android/data/${xaApp.packageName}/files/conf")
        if (confDir.isFile) {
            confDir.delete()
        }
        if (!confDir.exists()) {
            confDir.mkdirs()
        }
        val confFile = File(confDir, "conf.json")
        Log.d("XALog", conf.toJsonString())
        confFile.writeText(conf.toJsonString())
    }
}
