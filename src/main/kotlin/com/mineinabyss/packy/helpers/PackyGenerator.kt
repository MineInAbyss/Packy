package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import kotlinx.coroutines.*
import org.bukkit.entity.Player
import team.unnamed.creative.BuiltResourcePack
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackReader
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import team.unnamed.creative.sound.SoundRegistry
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

object PackyGenerator {

    val activeGeneratorJob: MutableMap<TemplateIds, Deferred<BuiltResourcePack>> = mutableMapOf()

    fun setupForcedPackFiles() {
        packy.plugin.launch(packy.plugin.asyncDispatcher) {
            packy.defaultPack.icon(Writable.path(packy.plugin.dataFolder.toPath() / packy.config.icon))
            packy.defaultPack.packMeta(packy.config.mcmeta.format, packy.config.mcmeta.description)

            // Add all forced packs to defaultPack
            packy.templates.filter { it.value.forced }.keys.forEach { id ->
                val templatePath = packy.plugin.dataFolder.toPath() / "templates" / id
                if (!templatePath.isDirectory() || templatePath.listDirectoryEntries().isEmpty()) return@forEach
                val templatePack = MinecraftResourcePackReader.minecraft().readFromDirectory(templatePath.toFile())
                packy.defaultPack.mergeWith(templatePack)
            }
            logSuccess("Finished configuring defaultPack")
        }
    }

    suspend fun getOrCreateCachedPack(player: Player): Deferred<BuiltResourcePack> = coroutineScope {
        val templateIds = player.packyData.enabledPackAddons.map { it.id }.toSet()
        PackyServer.cachedPacks[templateIds]?.let {
            return@coroutineScope async { it }
        }

        activeGeneratorJob.getOrPut(templateIds) {
            async(Dispatchers.IO) {
                val cachedPack = ResourcePack.resourcePack()
                cachedPack.mergeWith(packy.defaultPack)

                // Filters out all forced files as they are already in defaultPack
                // Filter all TemplatePacks that are not default or not in players enabledPackAddons
                packy.templates.entries.filterNot { it.value.forced }.filter { it.value in player.packyData.enabledPackAddons }.forEach { (id, template) ->
                    val templatePath = packy.plugin.dataFolder.toPath() / "templates" / id
                    if (!templatePath.isDirectory() || templatePath.listDirectoryEntries().isEmpty()) return@forEach
                    val templatePack = MinecraftResourcePackReader.minecraft().readFromDirectory(templatePath.toFile())
                    cachedPack.mergeWith(templatePack)
                }

                //val playerPacks = (packy.plugin.dataFolder.toPath() / "playerPacks" / player.uniqueId.toString()).toFile().apply { deleteRecursively() }
                //MinecraftResourcePackWriter.minecraft().writeToDirectory(playerPacks, playerPack)
                MinecraftResourcePackWriter.minecraft().build(cachedPack)
            }
        }
    }

    private fun ResourcePack.mergeWith(mergePack: ResourcePack) {
        mergePack.textures().forEach(this::texture)
        mergePack.sounds().forEach(this::sound)
        mergePack.unknownFiles().forEach(this::unknownFile)
        mergePack.packMeta()?.let { packMeta(it.formats(), it.description().ifEmpty { description() ?: "" }.miniMsg()) }
        mergePack.icon()?.let { icon(it) }

        mergePack.models().forEach { model ->
            val baseModel = model(model.key()) ?: return@forEach model(model)
            model(model.apply { overrides().addAll(baseModel.overrides()) })
        }
        mergePack.fonts().forEach { font ->
            val baseFont = font(font.key()) ?: return@forEach font(font)
            font(font.key(), baseFont.providers().apply { addAll(font.providers()) })
        }
        mergePack.soundRegistries().forEach { soundRegistry ->
            val baseRegistry = soundRegistry(soundRegistry.namespace()) ?: return@forEach soundRegistry(soundRegistry)
            soundRegistry(SoundRegistry.soundRegistry(soundRegistry.namespace(), baseRegistry.sounds().toMutableSet().apply { addAll(soundRegistry.sounds()) }))
        }
        mergePack.atlases().forEach { atlas ->
            val baseAtlas = atlas(atlas.key()) ?: return@forEach atlas(atlas)
            atlas.sources().forEach {
                baseAtlas.toBuilder().addSource(it)
            }
        }
        mergePack.languages().forEach { language ->
            val baseLanguage = language(language.key()) ?: return@forEach language(language)
            language(baseLanguage.apply { translations().putAll(language.translations()) })
        }
        mergePack.blockStates().forEach { blockState ->
            val baseBlockState = blockState(blockState.key()) ?: return@forEach blockState(blockState)
            blockState(baseBlockState.apply { variants().putAll(blockState.variants()) })
        }
    }
}
