package com.mineinabyss.packy.config

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.packy.helpers.PackyDownloader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.path.div
import kotlin.io.path.pathString

@Serializable
data class PackyTemplate(
    val id: String,
    val default: Boolean = false,
    val forced: Boolean,
    @EncodeDefault(NEVER) val conflictsWith: Set<String> = setOf(),
    @EncodeDefault(NEVER) val githubUrl: String? = null
)

@Serializable
data class PackyAccessToken(internal val token: String = "")

fun PackyTemplate.conflictsWith(template: PackyTemplate) =
    template.id in this.conflictsWith || this.id in template.conflictsWith

