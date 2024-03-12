package com.mineinabyss.packy.config

import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.plugin.Plugins
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.packy.listener.TemplateLoadTriggers
import com.ticxo.modelengine.api.events.ModelRegistrationEvent
import com.ticxo.modelengine.api.generator.ModelGenerator
import io.th0rgal.oraxen.api.events.OraxenPackGeneratedEvent
import io.th0rgal.oraxen.api.events.OraxenPackPreUploadEvent
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.nio.file.Path
import kotlin.io.path.*

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PackyTemplates(val templates: List<PackyTemplate> = listOf()) {
    @EncodeDefault(NEVER)
    val templateMap: Map<String, PackyTemplate> = templates.associateBy { it.name }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PackyTemplate(
    val name: String,
    val default: Boolean = false,
    val forced: Boolean = false,
    @EncodeDefault(NEVER) val conflictsWith: Set<String> = setOf(),
    @EncodeDefault(NEVER) val githubDownload: GithubDownload? = null,
    @EncodeDefault(NEVER) private val filePath: String? = null,
    @EncodeDefault(NEVER) val loadTrigger: LoadTrigger? = null
) {

    enum class LoadTrigger {
        MODELENGINE, HAPPYHUD, ORAXEN;

        fun registerLoadHandler() {
            when {
                this == MODELENGINE && Plugins.isEnabled("ModelEngine") -> object : Listener {
                    @EventHandler
                    fun ModelRegistrationEvent.onMegPackZipped() {
                        if (phase != ModelGenerator.Phase.POST_ZIPPING) return
                        logError("ModelEngine load trigger not yet implemented")
                    }
                }
                this == HAPPYHUD && Plugins.isEnabled("HappyHUD") -> {
                    logError("HappyHUD load trigger not yet implemented")
                }
                this == ORAXEN && Plugins.isEnabled("Oraxen") -> object : Listener {
                    @EventHandler
                    fun OraxenPackPreUploadEvent.onOraxenPackPreUpload() {
                        isCancelled = true
                        logError("Oraxen load trigger not yet implemented")
                    }
                }
            }
        }
    }

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

