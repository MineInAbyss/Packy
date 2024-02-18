package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.google.gson.internal.LazilyParsedNumber
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.config.packy
import kotlinx.coroutines.*
import team.unnamed.creative.BuiltResourcePack
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import team.unnamed.creative.sound.SoundRegistry
import kotlin.io.path.div
import kotlin.io.path.exists

@OptIn(ExperimentalCoroutinesApi::class)
object PackyGenerator {
    private val generatorDispatcher = Dispatchers.IO.limitedParallelism(1)
    val activeGeneratorJob: MutableMap<TemplateIds, Deferred<BuiltResourcePack>> = mutableMapOf()
    val cachedPacks: CacheMap<TemplateIds, BuiltResourcePack> = CacheMap(packy.config.cachedPackAmount)
    val cachedPacksByteArray: CacheMap<TemplateIds, ByteArray> = CacheMap(packy.config.cachedPackAmount)

    fun setupForcedPackFiles() {
        packy.plugin.launch(packy.plugin.asyncDispatcher) {
            (packy.plugin.dataFolder.toPath() / packy.config.icon).takeIf { it.exists() }
                ?.let { packy.defaultPack.icon(Writable.path(it)) }
            packy.config.mcmeta.description.takeIf { it.isNotEmpty() }
                ?.let { packy.defaultPack.packMeta(packy.config.mcmeta.format, it.miniMsg()) }

            // Add all forced packs to defaultPack
            packy.templates.filter { it.value.forced }.values.mapNotNull { it.path.toFile().readPack() }.forEach {
                packy.defaultPack.mergeWith(it)
            }

            logSuccess("Finished configuring defaultPack")
        }
    }

    suspend fun getOrCreateCachedPack(templateIds: TemplateIds): Deferred<BuiltResourcePack> = coroutineScope {
        PackyDownloader.startupJob?.join() // Ensure templates are downloaded

        // Make sure we read data on sync thread

        // Get cached pack or create an active job, we swap to one thread for safe hashmap access but run the generators async
        withContext(generatorDispatcher) {
            cachedPacks[templateIds]?.let { return@withContext async { it } }

            activeGeneratorJob.getOrPut(templateIds) {
                async(Dispatchers.IO) {
                    val cachedPack = ResourcePack.resourcePack()
                    cachedPack.mergeWith(packy.defaultPack)

                    // Filters out all forced files as they are already in defaultPack
                    // Filter all TemplatePacks that are not default or not in players enabledPackAddons
                    packy.templates.values.filter { !it.forced && it.id in templateIds }
                        .mapNotNull { it.path.toFile().readPack() }.forEach { cachedPack.mergeWith(it) }

                    cachedPack.sortItemOverrides()
                    //if (packy.config.packSquash.enabled) PackySquash.squashPack(cachedPack)
                    if (packy.config.obfuscate) PackObfuscator.obfuscatePack(cachedPack)
                    MinecraftResourcePackWriter.minecraft().build(cachedPack).apply {
                        cachedPacks[templateIds] = this
                        cachedPacksByteArray[templateIds] = this.data().toByteArray()
                    }
                }.also {
                    launch(generatorDispatcher) {
                        it.join()
                        activeGeneratorJob.remove(templateIds)
                    }
                }
            }
        }
    }

    /**
     * Ensures the ItemOverrides of a model are in numerical order for their CustomModelData
     */
    private fun ResourcePack.sortItemOverrides() {
        this.models().forEach { model ->
            val sortedOverrides = model.overrides().sortedBy { override ->
                // value() is a LazilyParsedNumber so convert it to an Int
                override.predicate().find { it.name() == "custom_model_data" }?.value()?.toString()?.toIntOrNull() ?: 0
            }
            this.model(model.toBuilder().overrides(sortedOverrides).build())
        }
    }

    private fun ResourcePack.mergeWith(mergePack: ResourcePack) {
        mergePack.textures().forEach(this::texture)
        mergePack.sounds().forEach(this::sound)
        mergePack.unknownFiles().forEach(this::unknownFile)

        mergePack.models().forEach { model ->
            val baseModel = model(model.key()) ?: return@forEach model(model)
            model(model.apply { overrides().addAll(baseModel.overrides()) })
        }
        mergePack.fonts().forEach { font ->
            val baseFont = font(font.key()) ?: return@forEach font(font)
            font(baseFont.apply { providers().addAll(font.providers()) })
        }
        mergePack.soundRegistries().forEach { soundRegistry ->
            val baseRegistry = soundRegistry(soundRegistry.namespace()) ?: return@forEach soundRegistry(soundRegistry)
            soundRegistry(
                SoundRegistry.soundRegistry(
                    soundRegistry.namespace(),
                    baseRegistry.sounds().toMutableSet().apply { addAll(soundRegistry.sounds()) })
            )
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
