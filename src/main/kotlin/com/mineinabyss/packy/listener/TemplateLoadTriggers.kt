package com.mineinabyss.packy.listener

import com.mineinabyss.idofront.di.DI
import com.mineinabyss.idofront.messaging.broadcast
import com.mineinabyss.idofront.plugin.Plugins
import com.mineinabyss.idofront.plugin.listeners
import com.mineinabyss.idofront.plugin.unregisterListeners
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.PackyGenerator
import com.mineinabyss.packy.PackySquash
import com.mineinabyss.packy.config.PackyContext
import com.mineinabyss.packy.listener.TemplateLoadTriggers.unregisterTemplateHandlers
import com.ticxo.modelengine.api.events.ModelRegistrationEvent
import com.ticxo.modelengine.api.generator.ModelGenerator
import io.lumine.mythiccrucible.events.MythicCrucibleGeneratePackEvent
import io.th0rgal.oraxen.OraxenPlugin
import io.th0rgal.oraxen.api.events.OraxenPackPreUploadEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kr.toxicity.hud.api.event.PluginReloadedEvent
import kr.toxicity.hud.api.plugin.ReloadState
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.part.ResourcePackPart

object TemplateLoadTriggers {

    fun registerTemplateHandlers() {
        unregisterTemplateHandlers()
        packy.templates.values.forEach { it.loadTrigger.registerLoadHandler(it) }
    }

    fun unregisterTemplateHandlers() {
        runCatching {
            packy.templates.values.forEach { t -> t.triggerListener?.let { packy.plugin.unregisterListeners(it) } }
        }
    }
}

@Serializable
sealed interface LoadTrigger {
    fun registerLoadHandler(template: PackyTemplate)

    @Serializable
    @SerialName("None")
    data object NoTrigger : LoadTrigger {
        override fun registerLoadHandler(template: PackyTemplate) {
        }
    }

    @Serializable
    @SerialName("ModelEngine")
    data object ModelEngineTrigger : LoadTrigger {
        override fun registerLoadHandler(template: PackyTemplate) {
            if (!Plugins.isEnabled("ModelEngine")) return

            val id = template.id
            unregisterTemplateHandlers()
            val listener = object : Listener {
                @EventHandler
                fun ModelRegistrationEvent.onMegPackZipped() {

                    if (phase != ModelGenerator.Phase.POST_ZIPPING) return
                    packy.logger.w("ModelEngine loadTrigger detected...")
                    val megPack = packy.plugin.server.pluginsFolder.resolve("ModelEngine/resource pack.zip").takeIf { it.exists() }
                        ?: return packy.logger.e("ModelEngine pack is missing, skipping loadTrigger for $id-template")
                    megPack.copyTo(template.path.toFile(), overwrite = true)

                    template.clearFromCache()
                    packy.logger.s("Copying ModelEngine-pack for $id-template")

                    if (packy.config.packSquash.enabled) {
                        packy.logger.i("Starting PackSquash process for $id-template...")
                        PackySquash.squashPackyTemplate(template)
                        packy.logger.s("Finished PackSquash process for $id-template")
                    }
                }
            }
            template.triggerListener = listener
            packy.plugin.listeners(listener)
        }
    }

    @Serializable
    @SerialName("Crucible")
    data object CrucibleTrigger : LoadTrigger {
        override fun registerLoadHandler(template: PackyTemplate) {
            if (!Plugins.isEnabled("MythicCrucible")) return

            val id = template.id
            unregisterTemplateHandlers()
            val listener = object : Listener {
                @EventHandler
                fun MythicCrucibleGeneratePackEvent.onCruciblePack() {
                    packy.logger.w("MythicCrucible loadTrigger detected...")
                    zippedPack?.copyTo(template.path.toFile(), true).takeIf { it?.exists() == true }
                        ?: return packy.logger.e("MythicCrucible-pack is missing, skipping loadTrigger for $id-template")

                    template.clearFromCache()
                    packy.logger.s("Copying MythicCrucible-pack for $id-template")

                    if (packy.config.packSquash.enabled) {
                        packy.logger.i("Starting PackSquash process for $id-template...")
                        PackySquash.squashPackyTemplate(template)
                        packy.logger.s("Finished PackSquash process for $id-template")
                    }
                }
            }
            template.triggerListener = listener
            packy.plugin.listeners(listener)
        }
    }

    @Serializable
    @SerialName("BetterHud")
    data object BetterHudTrigger : LoadTrigger {
        override fun registerLoadHandler(template: PackyTemplate) {
            if (!Plugins.isEnabled("BetterHud")) return

            val id = template.id
            unregisterTemplateHandlers()
            val listener = object : Listener {
                @EventHandler
                fun PluginReloadedEvent.onPluginReload() {
                    if (result.state != ReloadState.SUCCESS) return

                    packy.logger.w("BetterHud loadTrigger detected...")
                    template.clearFromCache()
                }
            }
            template.triggerListener = listener
            packy.plugin.listeners(listener)
        }
    }

    @Serializable
    @SerialName("Oraxen")
    data object OraxenTrigger : LoadTrigger {
        override fun registerLoadHandler(template: PackyTemplate) {
            if (!Plugins.isEnabled("Oraxen")) return

            val id = template.id
            unregisterTemplateHandlers()
            val listener = object : Listener {
                @EventHandler
                fun OraxenPackPreUploadEvent.onOraxenPackPreUpload() {
                    packy.logger.w("Oraxen loadTrigger detected...")
                    isCancelled = true
                    val oraxenPack = OraxenPlugin.get().resourcePack?.file?.takeIf { it.exists() }
                        ?: return packy.logger.e("Oraxen-pack is missing, skipping loadTrigger for $id-template")
                    oraxenPack.copyTo(template.path.toFile(), true)

                    template.clearFromCache()
                    packy.logger.s("Copying Oraxen-pack for $id-template")

                    if (packy.config.packSquash.enabled) {
                        packy.logger.i("Starting PackSquash process for $id-template...")
                        PackySquash.squashPackyTemplate(template)
                        packy.logger.s("Finished PackSquash process for $id-template")
                    }
                }
            }
            template.triggerListener = listener
            packy.plugin.listeners(listener)
        }
    }

    fun PackyTemplate.clearFromCache() {
        when {
            required -> packy.plugin.createPackyContext()
            else -> {
                PackyGenerator.cachedPacks.keys.removeIf { id in it }
                PackyGenerator.cachedPacksByteArray.keys.removeIf { id in it }
            }
        }
    }
}
