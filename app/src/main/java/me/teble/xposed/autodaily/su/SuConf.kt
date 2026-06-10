package me.teble.xposed.autodaily.su

import kotlinx.serialization.Serializable

@Serializable
data class SuConf(
    val enableKeepAlive: Boolean,
    val alivePackages: Map<String, Boolean>
)
