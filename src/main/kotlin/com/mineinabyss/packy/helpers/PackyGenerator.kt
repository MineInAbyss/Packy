package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyServer.playerPack
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import org.bukkit.entity.Player
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackReader
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import team.unnamed.creative.sound.SoundRegistry
import java.util.UUID
import kotlin.io.path.div

object PackyGenerator {

    val activeGeneratorJob: MutableMap<UUID, Job?> = mutableMapOf()

    fun setupForcedPackFiles() {
        packy.plugin.launch(packy.plugin.asyncDispatcher) {
            packy.defaultPack.icon(Writable.path(packy.plugin.dataFolder.toPath() / packy.config.icon))
            packy.defaultPack.packMeta(packy.config.mcmeta.format, packy.config.mcmeta.description)

            // Add all forced packs to defaultPack
            packy.templates.filter { it.value.forced }.keys.forEach { id ->
                val templatePath = packy.plugin.dataFolder.toPath() / "templates" / id
                val templatePack = MinecraftResourcePackReader.minecraft().readFromDirectory(templatePath.toFile())
                mergePacks(packy.defaultPack, templatePack)
                logSuccess("Added ${id}-template to defaultPack")
            }
        }
    }

    fun createPlayerPack(player: Player) {
        player.playerPack = null
        if (activeGeneratorJob[player.uniqueId] != null) return
        val playerPack = ResourcePack.resourcePack()
        val job = packy.plugin.launch(packy.plugin.asyncDispatcher, CoroutineStart.LAZY) {
            mergePacks(playerPack, packy.defaultPack)

            // Filters out all forced files as they are already in defaultPack
            // Filter all TemplatePacks that are not default or not in players enabledPackAddons
            packy.templates.entries.filterNot { it.value.forced }.filter { it.value in player.packyData.enabledPackAddons }.forEach { (id, template) ->
                val templatePath = packy.plugin.dataFolder.toPath() / "templates" / id
                val templatePack = MinecraftResourcePackReader.minecraft().readFromDirectory(templatePath.toFile())
                mergePacks(playerPack, templatePack)
                logSuccess("Added ${id}-template to pack")
            }

            //val playerPacks = (packy.plugin.dataFolder.toPath() / "playerPacks" / player.uniqueId.toString()).toFile().apply { deleteRecursively() }
            //MinecraftResourcePackWriter.minecraft().writeToDirectory(playerPacks, playerPack)
            player.playerPack = playerPack
        }

        activeGeneratorJob[player.uniqueId] = job
        job.start()
        job.invokeOnCompletion {
            activeGeneratorJob -= player.uniqueId
        }
    }

    private fun mergePacks(basePack: ResourcePack, mergePack: ResourcePack): ResourcePack {
        mergePack.textures().forEach(basePack::texture)
        mergePack.sounds().forEach(basePack::sound)
        mergePack.unknownFiles().forEach(basePack::unknownFile)
        mergePack.packMeta()?.let { basePack.packMeta(it.formats(), it.description().ifEmpty { basePack.description() ?: "" }.miniMsg()) }
        mergePack.icon()?.let { basePack.icon(it) }

        mergePack.models().forEach { model ->
            val baseModel = basePack.model(model.key()) ?: return@forEach basePack.model(model)
            basePack.model(model.apply { overrides().addAll(baseModel.overrides()) })
        }
        mergePack.fonts().forEach { font ->
            val baseFont = basePack.font(font.key()) ?: return@forEach basePack.font(font)
            basePack.font(font.key(), baseFont.providers().apply { addAll(font.providers()) })
        }
        mergePack.soundRegistries().forEach { soundRegistry ->
            val baseRegistry = basePack.soundRegistry(soundRegistry.namespace()) ?: return@forEach basePack.soundRegistry(soundRegistry)
            basePack.soundRegistry(SoundRegistry.soundRegistry(soundRegistry.namespace(), baseRegistry.sounds().toMutableSet().apply { addAll(soundRegistry.sounds()) }))
        }
        mergePack.atlases().forEach { atlas ->
            val baseAtlas = basePack.atlas(atlas.key()) ?: return@forEach basePack.atlas(atlas)
            atlas.sources().forEach {
                baseAtlas.toBuilder().addSource(it)
            }
        }
        mergePack.languages().forEach { language ->
            val baseLanguage = basePack.language(language.key()) ?: return@forEach basePack.language(language)
            basePack.language(baseLanguage.apply { translations().putAll(language.translations()) })
        }
        mergePack.blockStates().forEach { blockState ->
            val baseBlockState = basePack.blockState(blockState.key()) ?: return@forEach basePack.blockState(blockState)
            basePack.blockState(baseBlockState.apply { variants().putAll(blockState.variants()) })
        }

        return basePack
    }
}
