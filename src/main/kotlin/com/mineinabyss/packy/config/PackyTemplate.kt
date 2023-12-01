package com.mineinabyss.packy.config

import com.charleskorn.kaml.YamlComment
import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.packy.helpers.PackyDownloader
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

@Serializable
data class PackyTemplates(val templates: List<PackyTemplate> = listOf()) {
    @EncodeDefault(NEVER) val templateMap: Map<String, PackyTemplate> = templates.associateBy { it.name }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PackyTemplate(
    val name: String,
    val default: Boolean = false,
    val forced: Boolean = false,
    @EncodeDefault(NEVER) val conflictsWith: Set<String> = setOf(),
    @EncodeDefault(NEVER) val githubDownload: GithubDownload? = null,
    @EncodeDefault(NEVER) private val filePath: String? = null
) {
    val id: String get() = name

    val path: Path
        get() = filePath?.let { packy.plugin.dataFolder.parentFile.toPath() / it }
            ?: (packy.plugin.dataFolder.toPath() / "templates" / id)
                .let { if (it.exists() && it.isDirectory()) it else Path(it.pathString + ".zip") }

    @Serializable
    data class GithubDownload(
        val org: String,
        val repo: String,
        val branch: String,
        @EncodeDefault(NEVER) val subFolder: String? = null
    )
}

@Serializable
data class PackyAccessToken(internal val token: String = "")

fun PackyTemplate.conflictsWith(template: PackyTemplate) =
    template.id in this.conflictsWith || this.id in template.conflictsWith

