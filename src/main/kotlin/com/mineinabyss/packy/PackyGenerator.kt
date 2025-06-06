package com.mineinabyss.packy

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.google.gson.JsonParser
import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.idofront.util.filterFast
import com.mineinabyss.packy.components.PackyPack
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.AtlasGenerator
import com.mineinabyss.packy.helpers.CacheMap
import com.mineinabyss.packy.helpers.ModernVersionPatcher
import com.mineinabyss.packy.helpers.TemplateIds
import kotlinx.coroutines.*
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import kotlin.collections.containsValue
import kotlin.io.path.div
import kotlin.io.path.exists

object PackyGenerator {
    private val generatorDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var requiredTemplateJob: Job? = null
    val activeGeneratorJob: MutableMap<TemplateIds, Deferred<PackyPack>> = mutableMapOf()
    val cachedPacks: CacheMap<TemplateIds, PackyPack> = CacheMap(packy.config.cachedPackAmount)
    val cachedPacksByteArray: CacheMap<TemplateIds, ByteArray> = CacheMap(packy.config.cachedPackAmount)

    fun setupRequiredPackTemplates() {
        requiredTemplateJob?.cancel()
        requiredTemplateJob = packy.plugin.launch(packy.plugin.asyncDispatcher) {
            // Add all forced packs to defaultPack
            packy.templates.filter(PackyTemplate::required).mapNotNull { ResourcePacks.readToResourcePack(it.path.toFile()) }.forEach {
                ResourcePacks.mergeResourcePacks(packy.defaultPack, it)
            }

            packy.logger.s("Finished configuring defaultPack")
        }
    }

    fun getCachedPack(templateIds: TemplateIds): PackyPack? = cachedPacks[templateIds]

    suspend fun getOrCreateCachedPack(templateIds: TemplateIds): Deferred<PackyPack> = coroutineScope {
        PackyDownloader.startupJob?.join() // Ensure templates are downloaded
        requiredTemplateJob?.join()

        // Make sure we read data on sync thread

        // Get cached pack or create an active job, we swap to one thread for safe hashmap access but run the generators async
        withContext(generatorDispatcher) {
            cachedPacks[templateIds]?.let { return@withContext async { it } }

            activeGeneratorJob.getOrPut(templateIds) {
                async(Dispatchers.IO) {
                    val cachedPack = ResourcePack.resourcePack()
                    ResourcePacks.mergeResourcePacks(cachedPack, packy.defaultPack)

                    // Filters out all required files as they are already in defaultPack
                    // Filter all TemplatePacks that are not default or not in players enabledPackAddons
                    packy.templates.filter { !it.required && it.id in templateIds }
                        .mapNotNull { ResourcePacks.readToResourcePack(it.path.toFile()) }
                        .forEach { ResourcePacks.mergeResourcePacks(cachedPack, it) }

                    AtlasGenerator.generateAtlasFile(cachedPack)

                    ModernVersionPatcher.convertResources(cachedPack)
                    cachedPack.items().removeIf { ModernVersionPatcher.standardItemModels.containsValue(it) }
                    PackObfuscator(cachedPack).obfuscatePack()

                    (packy.plugin.dataFolder.toPath() / packy.config.icon).takeIf { it.exists() }
                        ?.let { packy.defaultPack.icon(Writable.path(it)) }
                    packy.config.mcmeta.description.takeIf { it.isNotEmpty() }
                        ?.let { packy.defaultPack.packMeta(packy.config.mcmeta.format, it.miniMsg()) }

                    ResourcePacks.resourcePackWriter.writeToZipFile(packy.plugin.dataFolder.resolve("test2.zip"), cachedPack)
                    val builtPack = ResourcePacks.resourcePackWriter.build(cachedPack)
                    PackyPack(builtPack, templateIds).apply {
                        cachedPacks[templateIds] = this
                        cachedPacksByteArray[templateIds] = builtPack.data().toByteArray()
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
}
