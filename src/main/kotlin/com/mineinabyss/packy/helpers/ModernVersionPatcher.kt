package com.mineinabyss.packy.helpers


import com.mineinabyss.idofront.messaging.idofrontLogger
import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.idofront.util.appendIfMissing
import com.mineinabyss.idofront.util.associateFast
import com.mineinabyss.idofront.util.filterFast
import com.mineinabyss.idofront.util.mapFast
import com.mineinabyss.idofront.util.prependIfMissing
import com.mineinabyss.packy.helpers.JsonBuilder.array
import com.mineinabyss.packy.helpers.JsonBuilder.`object`
import com.mineinabyss.packy.helpers.JsonBuilder.plus
import com.mineinabyss.packy.helpers.JsonBuilder.toJsonArray
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.model.ItemOverride
import team.unnamed.creative.model.ItemPredicate

// Patch for handling CustomModelData for 1.21.4+ until creative updated
class ModernVersionPatcher(val resourcePack: ResourcePack) {
    private val overlayItemModelRegex = ".+/assets/minecraft/items/.*\\.json".toRegex()

    private fun mergeItemModels(firstModel: Writable, secondModel: Writable): Writable {
        // Parse JSON objects
        val firstJson = firstModel.toJsonObject() ?: return secondModel
        val secondJson = secondModel.toJsonObject() ?: return firstModel
        if (firstJson == secondJson) return secondModel

        // Extract "entries" array from the first model
        val firstEntries = firstJson.`object`("model")?.array("entries")

        // If "entries" exist in the base model, merge with overlay
        if (firstEntries != null) {
            val secondModelJson = secondJson.`object`("model") ?: return firstModel
            val updatedEntries = secondModelJson.array("entries")?.plus(firstEntries) ?: firstEntries

            // Add merged entries back into the second model JSON
            secondModelJson.add("entries", updatedEntries)
            return Writable.stringUtf8(secondJson.toString())
        }

        // No "entries" in the base model, replace with overlay
        return secondModel
    }

    fun patchPack() {
        resourcePack.models().filterFast { it.key() in standardTextureModels }
            .associateFast { it.key().value().removePrefix("item/").appendIfMissing(".json") to it.overrides() }
            .forEach { (model, overrides) ->
                val standardItemModel = standardItemModels["assets/minecraft/items/$model"]?.toJsonObject()
                // If not standard (shield etc.) we need to traverse the tree
                val finalNewItemModel = standardItemModel?.let { existingItemModel ->
                    // More complex item-models, like shield etc
                    val baseItemModel = existingItemModel.`object`("model")?.takeUnless { it.isSimpleItemModel } ?: return@let null

                    runCatching {
                        val keys = baseItemModel.keySet()
                        when (model) {
                            "crossbow.json" -> handleCrossbowEntries(existingItemModel, baseItemModel, overrides)
                            "bow.json" -> handleBowEntries(existingItemModel, baseItemModel, overrides)
                            "player_head.json" -> handlePlayerEntries(existingItemModel, baseItemModel, overrides)
                            else -> {
                                if ("on_false" in keys) handleOnBoolean(false, baseItemModel, overrides)
                                if ("on_true" in keys) handleOnBoolean(true, baseItemModel, overrides)
                                if ("tints" in keys) handleTints(existingItemModel, baseItemModel, overrides)
                                if ("cases" in keys) handleCases(existingItemModel, baseItemModel, overrides)
                            }
                        }
                    }.onFailure {
                        it.printStackTrace()
                        idofrontLogger.e(model)
                        idofrontLogger.w(overrides.joinToString("\n") { s -> s.toString() })
                    }

                    existingItemModel
                } ?: JsonBuilder.jsonObject.plus(
                    "model",
                    modelObject(standardItemModel?.`object`("model"), overrides, model)
                )

                val key = "assets/minecraft/items/$model"
                val existingWritable = resourcePack.unknownFile(key)?.let {
                    if (!isStandardItemModel(key, it.toJsonObject())) return@let it
                    resourcePack.removeUnknownFile(key)
                    null
                }

                val finalWritable = existingWritable?.let {
                    mergeItemModels(it, Writable.stringUtf8(finalNewItemModel.toString()))
                } ?: Writable.stringUtf8(finalNewItemModel.toString())

                resourcePack.unknownFile(key, finalWritable)
            }

        // Merge any ItemModel in an overlay into the base one
        resourcePack.unknownFiles()
            .filterKeys { it.matches(overlayItemModelRegex) }
            .mapKeys { it.key.substringAfter("/").prependIfMissing("assets/") }
            .forEach { (key, overlayWritable) ->
                resourcePack.unknownFile(key)?.toJsonObject()
                    ?.takeUnless { !isStandardItemModel(key, it) }
                    ?.also { resourcePack.unknownFile(key, overlayWritable) }
                    ?: mergeItemModels(resourcePack.unknownFile(key) ?: overlayWritable, overlayWritable).also {
                        resourcePack.unknownFile(key, it)
                    }
            }

        // Remove all overlay ItemModels
        resourcePack.unknownFiles()
            .filterKeys { it.matches(overlayItemModelRegex) || resourcePack.unknownFile(it)?.let { w -> isStandardItemModel(it, w.toJsonObject()) } == true }
            .keys.forEach(resourcePack::removeUnknownFile)
    }

    private fun handleCases(
        existingItemModel: JsonObject,
        baseItemModel: JsonObject,
        overrides: MutableList<ItemOverride>,
    ) {
        JsonBuilder.jsonObject.plus("type", "minecraft:range_dispatch")
            .plus("property", "minecraft:custom_model_data")
            .plus("fallback", baseItemModel)
            .plus(
                "entries", JsonBuilder.jsonArray.plus(overrides.mapNotNull {
                    val cmd = it.predicate().customModelData ?: return@mapNotNull null
                    JsonBuilder.jsonObject.plus("threshold", cmd).plus(
                        "model", JsonBuilder.jsonObject
                            .plus("model", it.model().asString())
                            .plus("type", "minecraft:model")
                    )
                }.toJsonArray())
            ).let { existingItemModel.plus("model", it) }
    }

    private fun handleTints(
        existingItemModel: JsonObject,
        baseItemModel: JsonObject,
        overrides: MutableList<ItemOverride>,
    ) {
        val defaultTints = baseItemModel.array("tints") ?: return

        JsonBuilder.jsonObject.plus("type", "minecraft:range_dispatch")
            .plus("property", "minecraft:custom_model_data")
            .plus(
                "entries", JsonBuilder.jsonArray
                    .plus(JsonBuilder.jsonObject.plus("model", baseItemModel).plus("threshold", 0f))
                    .plus(overrides.mapNotNull {
                        val cmd = it.predicate().customModelData ?: return@mapNotNull null
                        JsonBuilder.jsonObject.plus("threshold", cmd).plus(
                            "model", JsonBuilder.jsonObject
                                .plus("model", it.model().asString())
                                .plus("type", "minecraft:model")
                                .plus("tints", defaultTints)
                        )
                    }.toJsonArray())
            ).let { existingItemModel.plus("model", it) }
    }

    private fun handleOnBoolean(
        boolean: Boolean,
        baseItemModel: JsonObject,
        overrides: List<ItemOverride>,
    ) {
        val defaultObject = baseItemModel.`object`("on_$boolean")?.deepCopy() ?: return
        val wantedOverrides = overrides.groupBy { it.predicate().customModelData }
            .let {
                it.values.map { e -> if (boolean) e.last() else e.first() }
            }.filter { (it.predicate().customModelData ?: 0) != 0 }

        JsonBuilder.jsonObject.plus("type", "minecraft:range_dispatch")
            .plus("property", "minecraft:custom_model_data")
            .plus(
                "entries", JsonBuilder.jsonArray
                    .plus(JsonBuilder.jsonObject.plus("model", defaultObject).plus("threshold", 0f))
                    .plus(wantedOverrides.mapNotNull {
                        val cmd = it.predicate().customModelData ?: return@mapNotNull null
                        JsonBuilder.jsonObject.plus("threshold", cmd).plus(
                            "model", JsonBuilder.jsonObject
                                .plus("model", it.model().asString())
                                .plus("type", "minecraft:model")
                        )
                    }.toJsonArray())
            ).let { baseItemModel.plus("on_$boolean", it) }
    }

    private fun handleCrossbowEntries(
        existingItemModel: JsonObject,
        baseItemModel: JsonObject,
        overrides: MutableList<ItemOverride>,
    ) {
        val defaultObject = baseItemModel.deepCopy() ?: return
        val pullingOverrides = overrides.groupBy { it.predicate().customModelData }

        JsonBuilder.jsonObject
            .plus("type", "minecraft:range_dispatch")
            .plus("property", "minecraft:custom_model_data")
            .plus("entries", pullingOverrides.entries.mapNotNull { (cmd, overrides) ->
                val pullOverrides = overrides.filter { it.predicate().pull != null }.sortedBy { it.predicate().pull }
                val pullFallback = pullOverrides.firstOrNull()?.model()?.asString() ?: overrides.firstOrNull()?.model()?.asString() ?: return@mapNotNull null
                val pullFallbackObject = JsonBuilder.jsonObject.plus("type", "minecraft:model").plus("model", pullFallback)

                val fallback = overrides.find { it.predicate().pull == null }?.model()?.asString()
                val fallbackObject = JsonBuilder.jsonObject.plus("type", "minecraft:model").plus("model", fallback)
                val fireworkObject = overrides.firstOrNull { it.predicate().firework != null }?.model()?.asString()?.let {
                    JsonBuilder.jsonObject
                        .plus("model", JsonBuilder.jsonObject.plus("type", "minecraft:model").plus("model", it))
                        .plus("when", "rocket")
                }
                val chargedObject = overrides.firstOrNull { it.predicate().charged != null }?.model()?.asString()?.let {
                    JsonBuilder.jsonObject
                        .plus("model", JsonBuilder.jsonObject.plus("type", "minecraft:model").plus("model", it))
                        .plus("when", "arrow")
                }

                JsonBuilder.jsonObject
                    .plus("threshold", cmd ?: return@mapNotNull null)
                    .plus(
                        "model",
                        JsonBuilder.jsonObject
                            .plus("type", "minecraft:condition")
                            .plus("property", "minecraft:using_item")
                            .plus("on_false",
                                JsonBuilder.jsonObject
                                    .plus("type", "minecraft:select")
                                    .plus("property", "minecraft:charge_type")
                                    .plus("fallback", fallbackObject)
                                    .plus("cases", JsonBuilder.jsonArray.plus(fireworkObject).plus(chargedObject).filterNotNull().toJsonArray())
                            )
                            .plus("on_true",
                                JsonBuilder.jsonObject
                                    .plus("type", "minecraft:range_dispatch")
                                    .plus("property", "minecraft:crossbow/pull")
                                    .plus("fallback", pullFallbackObject)
                                    .plus("entries", pullOverrides.mapNotNull pull@{ pull ->
                                        val model = pull.model().asString()
                                        val modelObject = JsonBuilder.jsonObject.plus("type", "minecraft:model").plus("model", model)
                                        val pullThreshold = pull.predicate().pull?.takeIf { it > 0 } ?: return@pull null
                                        JsonBuilder.jsonObject.plus("threshold", pullThreshold).plus("model", modelObject)
                                    }.toJsonArray())
                            )
                    )
            }.toJsonArray())
            .plus("fallback", defaultObject)
            .also { existingItemModel.plus("model", it) }
    }

    private fun handlePlayerEntries(
        existingItemModel: JsonObject,
        baseItemModel: JsonObject,
        overrides: List<ItemOverride>,
    ) {
        JsonBuilder.jsonObject.plus("type", "minecraft:range_dispatch")
            .plus("property", "minecraft:custom_model_data")
            .plus("fallback", baseItemModel)
            .plus(
                "entries", JsonBuilder.jsonArray
                    .plus(overrides.mapNotNull { override ->
                        val entry = baseItemModel.deepCopy().also {
                            it.addProperty("base", override.model().asString())
                        }
                        val cmd = override.predicate().customModelData ?: return@mapNotNull null
                        JsonBuilder.jsonObject.plus("threshold", cmd).plus("model", entry)
                    }.toJsonArray())
            ).let { existingItemModel.plus("model", it) }
    }

    private fun handleBowEntries(
        existingItemModel: JsonObject,
        baseItemModel: JsonObject,
        overrides: List<ItemOverride>,
    ) {
        val defaultObject = baseItemModel.deepCopy() ?: return
        val pullingOverrides = overrides.groupBy { it.predicate().customModelData }

        JsonBuilder.jsonObject
            .plus("type", "minecraft:range_dispatch")
            .plus("property", "minecraft:custom_model_data")
            .plus("entries", pullingOverrides.entries.mapNotNull { (cmd, overrides) ->
                val pullOverrides = overrides.filter { it.predicate().pull != null }.sortedBy { it.predicate().pull }
                val fallback = pullOverrides.firstOrNull()?.model()?.asString() ?: return@mapNotNull null
                val onFalse = overrides.firstOrNull()?.model()?.asString() ?: return@mapNotNull null

                JsonBuilder.jsonObject
                    .plus("threshold", cmd ?: return@mapNotNull null)
                    .plus(
                        "model",
                        JsonBuilder.jsonObject
                            .plus("type", "minecraft:condition")
                            .plus("property", "minecraft:using_item")
                            .plus("on_false", JsonBuilder.jsonObject.plus("model", onFalse).plus("type", "minecraft:model"))
                            .plus(
                                "on_true",
                                JsonBuilder.jsonObject
                                    .plus("type", "minecraft:range_dispatch")
                                    .plus("property", "minecraft:use_duration")
                                    .plus("scale", 0.05)
                                    .plus("fallback", JsonBuilder.jsonObject.plus("type", "minecraft:model").plus("model", fallback))
                                    .plus("entries", pullOverrides.mapNotNull pull@{ pull ->
                                        val model = pull.model().asString()
                                        val pull = pull.predicate().pull?.takeIf { it > 0 } ?: return@pull null
                                        JsonBuilder.jsonObject
                                            .plus("threshold", pull)
                                            .plus("model", JsonBuilder.jsonObject.plus("type", "minecraft:model").plus("model", model))
                                    }.toJsonArray())
                            )
                    )
            }.toJsonArray()).plus("fallback", defaultObject).also { existingItemModel.plus("model", it) }

    }

    private val JsonObject.isSimpleItemModel: Boolean
        get() = keySet().size == 2 && get("type").asString.equals("minecraft:model")


    private val standardTextureModels by lazy {
        ResourcePacks.defaultVanillaResourcePack.models().mapFast { it.key() }
    }

    companion object {
        private val standardItemModels by lazy {
            ResourcePacks.defaultVanillaResourcePack.unknownFiles().filterFast { it.key.startsWith("assets/minecraft/items") }
        }

        fun isStandardItemModel(key: String, itemModel: JsonObject?): Boolean {
            return (standardItemModels[key]?.toJsonObject()?.equals(itemModel) ?: false)
        }
    }

    private fun modelObject(
        baseItemModel: JsonObject?,
        overrides: List<ItemOverride>,
        model: String? = null,
    ): JsonObject = JsonBuilder.jsonObject
        .plus("type", "minecraft:range_dispatch")
        .plus("property", "minecraft:custom_model_data")
        .plus("entries", modelEntries(overrides))
        .plus("scale", 1f)
        .apply {
            if (model != null) plus(
                "fallback", baseItemModel ?: JsonBuilder.jsonObject
                    .plus("type", "minecraft:model")
                    .plus("model", "item/${model.removeSuffix(".json")}")
            )
        }

    private fun modelEntries(overrides: List<ItemOverride>) = overrides.mapNotNull {
        JsonBuilder.jsonObject.plus("threshold", it.predicate().customModelData ?: return@mapNotNull null).plus(
            "model", JsonBuilder.jsonObject
                .plus("model", it.model().asString())
                .plus("type", "minecraft:model")
        )
    }.toJsonArray()

    private val List<ItemPredicate>.customModelData: Float?
        get() = firstOrNull { it.name() == "custom_model_data" }?.value().toString().toFloatOrNull()
    private val List<ItemPredicate>.pull: Float?
        get() = firstOrNull { it.name() == "pull" }?.value().toString().toFloatOrNull()
    private val List<ItemPredicate>.charged: Double?
        get() = firstOrNull { it.name() == "charged" }?.value().toString().toDoubleOrNull()
    private val List<ItemPredicate>.firework: Double?
        get() = firstOrNull { it.name() == "firework" }?.value().toString().toDoubleOrNull()
}