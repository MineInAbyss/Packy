package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.messaging.broadcast
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import kotlinx.coroutines.*
import org.bukkit.entity.Player
import team.unnamed.creative.BuiltResourcePack
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import team.unnamed.creative.sound.SoundRegistry
import kotlin.io.path.div
import kotlin.io.path.exists

object PackyGenerator {

    val activeGeneratorJob: MutableMap<TemplateIds, Deferred<BuiltResourcePack>> = mutableMapOf()

    fun setupForcedPackFiles() {
        packy.plugin.launch(packy.plugin.asyncDispatcher) {
            (packy.plugin.dataFolder.toPath() / packy.config.icon).takeIf { it.exists() }?.let { packy.defaultPack.icon(Writable.path(it)) }
            packy.config.mcmeta.description.takeIf { it.isNotEmpty() }?.let { packy.defaultPack.packMeta(packy.config.mcmeta.format, it.miniMsg()) }

            // Add all forced packs to defaultPack
            packy.templates.filter { it.value.forced }.values.mapNotNull { it.path.toFile().readPack() }.forEach {
                packy.defaultPack.mergeWith(it)
            }

            logSuccess("Finished configuring defaultPack")
        }
    }

    suspend fun getOrCreateCachedPack(player: Player): Deferred<BuiltResourcePack> = coroutineScope {
        val templateIds = player.packyData.enabledPackIds
        PackyDownloader.startupJob?.join()
        PackyServer.cachedPacks[templateIds]?.let { return@coroutineScope async { it } }

        // Ensure templates are downloaded

        activeGeneratorJob.getOrPut(templateIds) {
            async(Dispatchers.IO) {
                val cachedPack = ResourcePack.resourcePack()
                cachedPack.mergeWith(packy.defaultPack)

                // Filters out all forced files as they are already in defaultPack
                // Filter all TemplatePacks that are not default or not in players enabledPackAddons
                packy.templates.values.filter { !it.forced && it.id in templateIds }
                    .mapNotNull { it.path.toFile().readPack() }.forEach { cachedPack.mergeWith(it) }

                cachedPack.sortItemOverrides()
                if (packy.config.obfuscate) PackObfuscator.obfuscatePack(cachedPack)
                MinecraftResourcePackWriter.minecraft().writeToZipFile(packy.plugin.dataFolder.resolve("pack.zip"), cachedPack)
                MinecraftResourcePackWriter.minecraft().build(cachedPack).apply {
                    PackyServer.cachedPacks[templateIds] = this
                    PackyServer.cachedPacksByteArray[templateIds] = this.data().toByteArray()
                }
            }
        }
    }

    /**
     * Ensures the ItemOverrides of a model are in numerical order for their CustomModelData
     */
    private fun ResourcePack.sortItemOverrides() {
        this.models().forEach { model ->
            val sortedOverrides = model.overrides().sortedBy { it.predicate().customModelData() }
            this.model(model.toBuilder().overrides(sortedOverrides).build())
        }
    }

    private fun ResourcePack.mergeWith(mergePack: ResourcePack) {
        mergePack.textures().forEach(this::texture)
        mergePack.sounds().forEach(this::sound)
        mergePack.unknownFiles().forEach(this::unknownFile)

        mergePack.models().forEach { model ->
            val baseModel = model(model.key()) ?: return@forEach model(model)
            model(baseModel.apply { overrides().addAll(model.overrides()) })
        }
        mergePack.fonts().forEach { font ->
            val baseFont = font(font.key()) ?: return@forEach font(font)
            font(baseFont.apply { providers().addAll(font.providers()) })
        }
        mergePack.soundRegistries().forEach { soundRegistry ->
            val baseRegistry = soundRegistry(soundRegistry.namespace()) ?: return@forEach soundRegistry(soundRegistry)
            soundRegistry(SoundRegistry.soundRegistry(soundRegistry.namespace(), baseRegistry.sounds().toMutableSet().apply { addAll(soundRegistry.sounds()) }))
        }
        mergePack.atlases().forEach { atlas ->
            val baseAtlas = atlas(atlas.key())?.toBuilder() ?: return@forEach atlas(atlas)
            atlas.sources().forEach(baseAtlas::addSource)
            atlas(baseAtlas.build())
        }
        mergePack.languages().forEach { language ->
            val baseLanguage = language(language.key()) ?: return@forEach language(language)
            language(baseLanguage.apply { translations().putAll(language.translations()) })
        }
        mergePack.blockStates().forEach { blockState ->
            val baseBlockState = blockState(blockState.key()) ?: return@forEach blockState(blockState)
            blockState(baseBlockState.apply { variants().putAll(blockState.variants()) })
        }

        if (packMeta()?.description().isNullOrEmpty()) mergePack.packMeta()?.let { packMeta(it) }
        if (icon() == null) mergePack.icon()?.let { icon(it) }
    }
}
