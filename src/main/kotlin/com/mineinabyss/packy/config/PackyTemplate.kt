package com.mineinabyss.packy.config

import com.mineinabyss.geary.serialization.serializers.InnerSerializer
import com.mineinabyss.geary.serialization.serializers.PolymorphicListAsMapSerializer
import com.mineinabyss.idofront.messaging.broadcastVal
import com.mineinabyss.packy.listener.LoadTrigger
import kotlinx.serialization.*
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.bukkit.event.Listener
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

@Serializable(with = PackyTemplates.Serializer::class)
data class PackyTemplates(private val templates: List<PackyTemplate> = listOf()) {

    @Transient private val ids = templates.map { it.id }

    operator fun contains(template: @UnsafeVariance PackyTemplate): Boolean = templates.contains(template)
    operator fun contains(id: @UnsafeVariance String): Boolean = ids.contains(id)
    fun forEach(action: (PackyTemplate) -> Unit) = templates.forEach(action)
    fun filter(filter: (PackyTemplate) -> Boolean) = templates.filter(filter)
    fun find(predicate: (PackyTemplate) -> Boolean) = templates.find(predicate)
    operator fun get(id: String) = templates.find { it.id == id }

    class Serializer : InnerSerializer<Map<String, PackyTemplate>, PackyTemplates>(
        "packy:templates",
        MapSerializer(String.serializer(), PackyTemplate.serializer()),
        { PackyTemplates(it.map { it.value.copy(id = it.key) }) },
        { it.templates.associateBy { it.id } }
    )
}

@Serializable
@Polymorphic
@OptIn(ExperimentalSerializationApi::class)
data class PackyTemplate(
    // This is blank by default to avoid marking it as null
    // The Serializer in PackyTemplates will always ensure the id is properly set
    @Transient val id: String = "",
    val default: Boolean = false,
    val required: Boolean = false,
    @EncodeDefault(NEVER) val conflictsWith: Set<String> = setOf(),
    @EncodeDefault(NEVER) val githubDownload: GithubDownload? = null,
    @EncodeDefault(NEVER) val loadTrigger: LoadTrigger = LoadTrigger.NoTrigger,
    @EncodeDefault(NEVER) private val filePath: String? = null
) {

    @Transient var triggerListener: Listener? = null

    val path: Path get() = filePath?.takeIf { it.isNotEmpty() }?.let { packy.plugin.dataFolder.parentFile.toPath() / it }
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

