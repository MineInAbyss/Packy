package com.mineinabyss.packy.config

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.packy.helpers.PackyDownloader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.path.div
import kotlin.io.path.pathString

@Serializable data class PackyTemplates(val templates: Map<String, PackyTemplate> = mapOf())
@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PackyTemplate(
    val default: Boolean = false,
    val forced: Boolean,
    @EncodeDefault(NEVER) val conflictsWith: Set<String> = setOf(),
    @EncodeDefault(NEVER) val githubUrl: String? = null
) {
    val id: String get() = packy.templates.entries.first { it.value == this }.key
}

@Serializable
data class PackyAccessToken(internal val token: String = "")

fun PackyTemplate.conflictsWith(template: PackyTemplate) =
    template.id in this.conflictsWith || this.id in template.conflictsWith

