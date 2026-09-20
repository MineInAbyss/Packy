package com.mineinabyss.packy.listener

import com.mineinabyss.idofront.plugin.Plugins
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.idofront.plugin.unregisterListeners
import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.packy.PackyGenerator
import com.mineinabyss.packy.PackySquash
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import com.nexomc.nexo.api.NexoPack
import com.nexomc.nexo.api.events.resourcepack.NexoPackFinishedEvent
import com.ticxo.modelengine.api.ModelEngineAPI
import com.ticxo.modelengine.api.events.ModelRegistrationEvent
import com.ticxo.modelengine.api.generator.ModelGenerator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import team.unnamed.creative.ResourcePack

object TemplateLoadTriggers {

    fun registerTemplateHandlers() {
        unregisterTemplateHandlers()
        packy.templates.forEach { if (it.loadTrigger.enabled) it.loadTrigger.registerLoadHandler(it) }
    }

    fun unregisterTemplateHandlers() {
        runCatching {
            packy.templates.forEach { t -> t.triggerListener?.let { packy.unregisterListeners(it) } }
        }
    }
}

@Serializable
sealed interface LoadTrigger {
    val enabled: Boolean get() = true

    fun registerLoadHandler(template: PackyTemplate)

    /** Reads the template straight from the trigger-plugin, null falls back to reading [PackyTemplate.path] */
    fun readPack(template: PackyTemplate): ResourcePack? = null

    @Serializable
    @SerialName("None")
    data object NoTrigger : LoadTrigger {
        override fun registerLoadHandler(template: PackyTemplate) {
        }
    }

    @Serializable
    @SerialName("ModelEngine")
    data object ModelEngineTrigger : LoadTrigger {
        override val enabled get() = packy.config.loadTriggers.modelEngine

        override fun registerLoadHandler(template: PackyTemplate) {
            if (!Plugins.isEnabled("ModelEngine")) return

            val id = template.id
            val listener = object : Listener {
                @EventHandler
                fun ModelRegistrationEvent.onMegPackZipped() {

                    if (phase != ModelGenerator.Phase.FINISHED) return
                    packy.logger.w("ModelEngine loadTrigger detected...")
                    val megPack = packy.server.pluginsFolder.resolve("ModelEngine/resource pack.zip").takeIf { it.exists() }
                        ?: packy.server.pluginsFolder.resolve("ModelEngine/resource pack").takeIf { it.exists() }
                        ?: return packy.logger.e("ModelEngine pack is missing, skipping loadTrigger for $id-template")
                    megPack.copyTo(template.path.toFile(), overwrite = true)
                    packy.logger.s("Copying ModelEngine-pack for $id-template")
                    template.squashTemplate()
                    template.refreshTemplate()
                }
            }
            template.triggerListener = listener
            packy.listeners(listener)
        }
    }

    @Serializable
    @SerialName("Nexo")
    data object NexoTrigger : LoadTrigger {
        override val enabled get() = packy.config.loadTriggers.nexo

        override fun registerLoadHandler(template: PackyTemplate) {
            if (!Plugins.isEnabled("Nexo")) return

            val listener = object : Listener {
                @EventHandler
                fun NexoPackFinishedEvent.onNexoPackFinished() {
                    packy.logger.w("Nexo loadTrigger detected...")
                    template.refreshTemplate()
                }
            }
            template.triggerListener = listener
            packy.listeners(listener)
        }

        override fun readPack(template: PackyTemplate): ResourcePack? {
            if (!Plugins.isEnabled("Nexo")) return null

            // Nexo keeps its finished pack in memory, so the template is read from there rather than from a copy on disk
            val builtPack = NexoPack.builtResourcePack()
                ?: return null.also { packy.logger.w("Nexo has not finished generating its pack, skipping ${template.id}-template") }

            return runCatching {
                builtPack.data().toByteArray().inputStream().use(ResourcePacks.resourcePackReader::readFromInputStream)
            }.onFailure { packy.logger.e("Failed to read Nexo-pack for ${template.id}-template: ${it.message}") }.getOrNull()
        }
    }

    fun PackyTemplate.clearFromCache() {
        PackyGenerator.cachedPacks.keys.removeIf { id in it }
        PackyGenerator.cachedPacksByteArray.keys.removeIf { id in it }
    }

    fun PackyTemplate.refreshTemplate() {
        // Required templates are baked into the defaultPack, so that has to be rebuilt to pick up the new
        // content. Cached packs are keyed by the addons a player enabled and never name a required template,
        // so dropping them by id would miss all of them, the rebuild clears them outright instead
        if (required) PackyGenerator.setupRequiredPackTemplates()
        else clearFromCache()
    }

    fun PackyTemplate.squashTemplate() {
        if (!packy.config.packSquash.enabled) return

        packy.logger.i("Starting PackSquash process for $id-template...")
        PackySquash.squashPackyTemplate(this)
        packy.logger.s("Finished PackSquash process for $id-template")
    }
}
