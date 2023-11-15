package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.messaging.broadcast
import com.mineinabyss.idofront.messaging.logInfo
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.messaging.logWarn
import com.mineinabyss.idofront.plugin.Plugins
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import com.ticxo.modelengine.api.ModelEngineAPI
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
import kotlin.io.path.notExists

object PackyGenerator {

    val activeGeneratorJob: MutableMap<TemplateIds, Deferred<BuiltResourcePack>> = mutableMapOf()

    fun setupForcedPackFiles() {
        packy.plugin.launch(packy.plugin.asyncDispatcher) {
            packy.defaultPack.icon(Writable.path(packy.plugin.dataFolder.toPath() / packy.config.icon))
            packy.defaultPack.packMeta(packy.config.mcmeta.format, packy.config.mcmeta.description)

            // Add all forced packs to defaultPack
            packy.templates.filter { it.value.forced }.values.forEach { template ->
                template.path.toFile().readPack()?.let { packy.defaultPack.mergeWith(it) }
            }

            if (packy.config.autoImportModelEngine && Plugins.isEnabled("ModelEngine")) {
                val modelEnginePack = ModelEngineAPI.getAPI().dataFolder.resolve("resource pack.zip")
                modelEnginePack.readPack()?.let {
                    packy.defaultPack.mergeWith(it)
                    logSuccess("Automatically merged ModelEngine-Resourcepack into defaultPack")
                } ?: logWarn("Failed to import ModelEngine-Resourcepack into defaultPack")
            }

            logSuccess("Finished configuring defaultPack")
        }
    }

    suspend fun getOrCreateCachedPack(player: Player): Deferred<BuiltResourcePack> = coroutineScope {
        val templateIds = player.packyData.enabledPackIds
        PackyServer.cachedPacks[templateIds]?.let { return@coroutineScope async { it } }

        activeGeneratorJob.getOrPut(templateIds) {
            async(Dispatchers.IO) {
                val cachedPack = ResourcePack.resourcePack()
                cachedPack.mergeWith(packy.defaultPack)

                // Filters out all forced files as they are already in defaultPack
                // Filter all TemplatePacks that are not default or not in players enabledPackAddons
                packy.templates.values.filter { !it.forced && it in player.packyData.enabledPackAddons }.map { it.path }.forEach { path ->
                    if (!path.isDirectory() || path.listDirectoryEntries().isEmpty()) return@forEach
                    val templatePack = MinecraftResourcePackReader.minecraft().readFromDirectory(path.toFile())
                    cachedPack.mergeWith(templatePack)
                }

                //MinecraftResourcePackWriter.minecraft().writeToDirectory((packy.plugin.dataFolder.toPath() / "playerPacks" / player.uniqueId.toString()).toFile(), cachedPack)
                MinecraftResourcePackWriter.minecraft().build(cachedPack).apply {
                    PackyServer.cachedPacksByteArray[templateIds] = this.data().toByteArray()
                }
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
