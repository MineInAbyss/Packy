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
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Vector3Float
import team.unnamed.creative.base.Writable
import team.unnamed.creative.item.ConditionItemModel
import team.unnamed.creative.item.EmptyItemModel
import team.unnamed.creative.item.Item
import team.unnamed.creative.item.ItemModel
import team.unnamed.creative.item.RangeDispatchItemModel
import team.unnamed.creative.item.ReferenceItemModel
import team.unnamed.creative.item.SelectItemModel
import team.unnamed.creative.item.SpecialItemModel
import team.unnamed.creative.item.property.ItemNumericProperty
import team.unnamed.creative.item.property.ItemStringProperty
import team.unnamed.creative.item.special.HeadSpecialRender
import team.unnamed.creative.model.ItemOverride
import team.unnamed.creative.model.ItemPredicate
import team.unnamed.creative.model.ItemTransform
import team.unnamed.creative.model.Model
import kotlin.collections.get
import kotlin.plus
import kotlin.text.contains
import kotlin.text.equals
import kotlin.text.get
import kotlin.toString

object ModernVersionPatcher {

    fun convertResources(resourcePack: ResourcePack) {
        resourcePack.models().associateBy(itemKeyPredicate).forEach { (itemKey, model) ->
            val standardItem = resourcePack.item(itemKey) ?: standardItemModels[itemKey]
            val overrides = model.overrides().ifEmpty {
                if (standardItem?.model() !is SpecialItemModel) return@forEach
                if (DisplayProperties.fromModel(model) == model.display()) return@forEach
                listOf()
            }
            val handSwap = standardItem?.handAnimationOnSwap() ?: Item.DEFAULT_HAND_ANIMATION_ON_SWAP
            val oversizedInGui = when {
                itemKey.value() == "player_head" && model.display()[ItemTransform.Type.GUI]?.scale() != Vector3Float.ONE -> true
                else -> standardItem?.oversizedInGui() ?: Item.DEFAULT_OVERSIZED_IN_GUI
            }

            val finalNewItemModel = standardItem?.let { existingItemModel ->
                val baseItemModel = existingItemModel.model().takeUnless(simpleItemModelPredicate) ?: return@let null

                when (baseItemModel) {
                    is RangeDispatchItemModel -> {
                        val fallback = baseItemModel.fallback() ?: baseItemModel
                        val entries = baseItemModel.entries().plus(overrides.mapNotNull {
                            val model = when {
                                fallback is SpecialItemModel && fallback.render() is HeadSpecialRender -> ItemModel.special(fallback.render(), it.model())
                                else -> ItemModel.reference(it.model())
                            }
                            RangeDispatchItemModel.Entry.entry(it.predicate().customModelData ?: return@mapNotNull null, model)
                        }).distinctBy { it.threshold() }
                        ItemModel.rangeDispatch(ItemNumericProperty.customModelData(), 1f, entries, fallback)
                    }

                    is SelectItemModel -> {
                        ItemModel.rangeDispatch(ItemNumericProperty.customModelData(), 1f, overrides.mapNotNull { override ->
                            val model = when {
                                baseItemModel is SpecialItemModel && baseItemModel.render() is HeadSpecialRender -> ItemModel.special(baseItemModel.render(), override.model())
                                else -> ItemModel.reference(override.model())
                            }
                            RangeDispatchItemModel.Entry.entry(override.predicate().customModelData ?: return@mapNotNull null, model)
                        }, baseItemModel)
                    }

                    is ConditionItemModel -> {
                        val (trueOverrides, falseOverrides) = overrides.groupByFast { it.predicate().customModelData?.takeUnless { it == 0f } }.let { grouped ->
                            when {
                                itemKey.asString().endsWith("bow") ->
                                    grouped.values.flatMap { it.filter { it.pulling } } to grouped.values.flatMap { it.filterNot { it.pulling } }

                                itemKey.asString().endsWith("shield") ->
                                    grouped.values.mapNotNull { it.firstOrNull { it.blocking } } to grouped.values.mapNotNull { it.firstOrNull { !it.blocking } }

                                itemKey.asString().endsWith("fishing_rod") ->
                                    grouped.values.mapNotNull { it.firstOrNull { it.cast } } to grouped.values.mapNotNull { it.firstOrNull { !it.cast } }

                                else -> grouped.values.mapNotNull { it.firstOrNull() } to grouped.values.mapNotNull { it.lastOrNull() }
                            }
                        }

                        // If there are any pull-override predicates it is a bow, and we build a RangeDispatchItemModel for the Entry, otherwise a simple ReferenceItemModel
                        val onTrueCmdEntries = trueOverrides.groupBy { it.predicate().customModelData }.mapNotNull { (cmd, overrides) ->
                            val baseOverrideModel = overrides.firstOrNull()?.let { ItemModel.reference(it.model()) } ?: return@mapNotNull null
                            val scale = RangeDispatchItemModel.DEFAULT_SCALE.takeUnless { itemKey.asMinimalString() == "bow" } ?: 0.05f

                            // If the overrides contain any pull, we make a bow-type model
                            val finalModel = overrides.drop(1).mapNotNull {
                                RangeDispatchItemModel.Entry.entry(it.predicate().pull ?: return@mapNotNull null, ItemModel.reference(it.model()))
                            }.takeUnless { it.isEmpty() }?.let { pullingEntries ->
                                val property = if ("crossbow" in itemKey.asString()) ItemNumericProperty.crossbowPull() else ItemNumericProperty.useDuration()
                                ItemModel.rangeDispatch(property, scale, pullingEntries, baseOverrideModel)
                            } ?: baseOverrideModel

                            RangeDispatchItemModel.Entry.entry(cmd ?: return@mapNotNull null, finalModel)
                        }

                        val onFalseCmdEntries = falseOverrides.groupBy { it.predicate().customModelData }.mapNotNull { (cmd, overrides) ->
                            val baseOverrideModel = overrides.firstOrNull()?.let { ItemModel.reference(it.model()) } ?: return@mapNotNull null
                            val firework = overrides.firstNotNullOfOrNull { it.firework }
                            val charged = overrides.firstNotNullOfOrNull { it.charged }

                            val finalModel = when {
                                charged != null || firework != null -> ItemModel.select().property(ItemStringProperty.chargeType()).fallback(baseOverrideModel).apply {
                                    charged?.apply(::addCase)
                                    firework?.apply(::addCase)
                                }.build()
                                else -> baseOverrideModel
                            }

                            RangeDispatchItemModel.Entry.entry(cmd ?: return@mapNotNull null, finalModel)
                        }

                        val onTrue = ItemModel.rangeDispatch(ItemNumericProperty.customModelData(), RangeDispatchItemModel.DEFAULT_SCALE, onTrueCmdEntries, baseItemModel.onTrue())
                        val onFalse = ItemModel.rangeDispatch(ItemNumericProperty.customModelData(), RangeDispatchItemModel.DEFAULT_SCALE, onFalseCmdEntries, baseItemModel.onFalse())

                        ItemModel.conditional(baseItemModel.condition(), onTrue, onFalse)
                    }

                    is ReferenceItemModel -> {
                        ItemModel.rangeDispatch(ItemNumericProperty.customModelData(), RangeDispatchItemModel.DEFAULT_SCALE, overrides.mapNotNull { override ->
                            val cmd = override.predicate().customModelData ?: return@mapNotNull null
                            RangeDispatchItemModel.Entry.entry(cmd, ItemModel.reference(override.model(), baseItemModel.tints()))
                        }, baseItemModel)
                    }

                    is SpecialItemModel -> {
                        val defaultDisplay = DisplayProperties.fromModel(model)
                        val newBase = ItemModel.special(baseItemModel.render(), when {
                            model.display() != defaultDisplay -> model.key()
                            else -> baseItemModel.base()
                        })

                        if (overrides.isNotEmpty()) ItemModel.rangeDispatch(ItemNumericProperty.customModelData(), RangeDispatchItemModel.DEFAULT_SCALE, overrides.mapNotNull { override ->
                            val cmd = override.predicate().customModelData ?: return@mapNotNull null
                            RangeDispatchItemModel.Entry.entry(cmd, ItemModel.special(baseItemModel.render(), override.model()))
                        }, newBase)
                        else newBase
                    }
                    else -> baseItemModel
                }
            } ?: modelObject(standardItem?.model(), overrides, itemKey)

            resourcePack.item(Item.item(itemKey, finalNewItemModel, handSwap, oversizedInGui))
        }
    }

    private fun modelObject(baseItemModel: ItemModel?, overrides: List<ItemOverride>, modelKey: Key?): ItemModel {
        return ItemModel.rangeDispatch().property(ItemNumericProperty.customModelData()).apply {
            if (modelKey != null) fallback(baseItemModel ?: ItemModel.reference(modelKey))
        }.addEntries(modelEntries(overrides)).build()
    }

    private fun modelEntries(overrides: List<ItemOverride>) = overrides.mapNotNull { override ->
        RangeDispatchItemModel.Entry.entry(override.predicate().customModelData ?: return@mapNotNull null, ItemModel.reference(override.model()))
    }

    private val List<ItemPredicate>.customModelData: Float?
        get() = firstOrNull { it.name() == "custom_model_data" }?.value().toString().toFloatOrNull()
    private val List<ItemPredicate>.pull: Float?
        get() = firstOrNull { it.name() == "pull" }?.value().toString().toFloatOrNull()
    private val ItemOverride.pulling: Boolean
        get() = predicate().any { it.name() == "pulling" }
    private val ItemOverride.charged: SelectItemModel.Case?
        get() = takeIf { it.predicate().any { it.name() == "charged" } }?.model()?.let { SelectItemModel.Case._case(ItemModel.reference(it), "arrow") }
    private val ItemOverride.firework: SelectItemModel.Case?
        get() = takeIf { it.predicate().any { it.name() == "firework" } }?.model()?.let { SelectItemModel.Case._case(ItemModel.reference(it), "rocket") }
    private val ItemOverride.blocking: Boolean
        get() = predicate().any { it.name() == "blocking" }
    private val ItemOverride.cast: Boolean
        get() = predicate().any { it.name() == "cast" }

    val standardItemModels by lazy {
        ResourcePacks.vanillaResourcePack.items().associateByTo(Object2ObjectOpenHashMap()) { it.key() }.minus(ResourcePacks.EMPTY_MODEL)
    }

    private val simpleItemModelPredicate = { item: ItemModel -> item is ReferenceItemModel && item.tints().isEmpty()}
    private val itemKeyPredicate = { model: Model -> Key.key(model.key().asString().replace("block/", "").replace("item/", "")) }
}