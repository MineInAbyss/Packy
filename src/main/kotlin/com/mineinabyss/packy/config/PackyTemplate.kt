package com.mineinabyss.packy.config

import com.mineinabyss.packy.listener.LoadTrigger
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bukkit.event.Listener
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PackyTemplates(val templates: List<PackyTemplate> = listOf()) {
    @EncodeDefault(NEVER)
    val templateMap: Map<String, PackyTemplate> = templates.associateBy { it.name }

    fun component2(): Map<String, PackyTemplate> = templateMap
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PackyTemplate(
    val name: String,
    val default: Boolean = false,
    val required: Boolean = false,
    @EncodeDefault(NEVER) val conflictsWith: Set<String> = setOf(),
    @EncodeDefault(NEVER) val githubDownload: GithubDownload? = null,
    @EncodeDefault(NEVER) val loadTrigger: LoadTrigger = LoadTrigger.NoTrigger,
    @EncodeDefault(NEVER) private val filePath: String? = null
) {

    @Transient var triggerListener: Listener? = null

    val id: String get() = name

    @Transient val path: Path = filePath?.takeIf { it.isNotEmpty() }?.let { packy.plugin.dataFolder.parentFile.toPath() / it }
        ?: (packy.plugin.dataFolder.toPath() / "templates" / id)
            .let { if (it.exists() && it.isDirectory()) it else Path(it.pathString + ".zip") }

    @Serializable
    data class GithubDownload(
        val org: String,
        val repo: String,
        val branch: String,
        @EncodeDefault(NEVER) val subFolder: String? = null
    ) {
        fun key(): GithubDownloadKey = GithubDownloadKey(org, repo, branch)
        data class GithubDownloadKey(val org: String, val repo: String, val branch: String)
    }
}

@Serializable
data class PackyAccessToken(internal val token: String = "")

fun PackyTemplate.conflictsWith(template: PackyTemplate) =
    template.id in this.conflictsWith || this.id in template.conflictsWith

