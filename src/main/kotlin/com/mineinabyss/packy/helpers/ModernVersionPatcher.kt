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
import team.unnamed.creative.model.ItemOverride
import team.unnamed.creative.model.ItemPredicate
import kotlin.collections.get
import kotlin.plus
import kotlin.text.contains
import kotlin.text.equals
import kotlin.text.get
import kotlin.toString

// Patch for handling CustomModelData for 1.21.4+ until creative updated
object ModernVersionPatcher {
    fun convertResources(resourcePack: ResourcePack) {
        resourcePack.models().associateBy { Key.key(it.key().asString().replace("block/", "").replace("item/", "")) }.forEach { (itemKey, model) ->
            val overrides = model.overrides().takeUnless { it.isEmpty() } ?: return@forEach
            val standardItem = resourcePack.item(itemKey) ?: standardItemModels[itemKey]
            val finalNewItemModel = standardItem?.let { existingItemModel ->
                val baseItemModel = existingItemModel.model().takeUnless { it.isSimpleItemModel } ?: return@let null

                when (baseItemModel) {
                    is RangeDispatchItemModel -> ItemModel.rangeDispatch(ItemNumericProperty.customModelData(), 1f, overrides.mapNotNull {
                        RangeDispatchItemModel.Entry.entry(it.predicate().customModelData ?: return@mapNotNull null, ItemModel.reference(it.model()))
                    }, baseItemModel)

                    is SelectItemModel -> ItemModel.rangeDispatch(ItemNumericProperty.customModelData(), 1f, overrides.mapNotNull { override ->
                        RangeDispatchItemModel.Entry.entry(override.predicate().customModelData ?: return@mapNotNull null, ItemModel.reference(override.model()))
                    }, baseItemModel)

                    is ConditionItemModel -> {
                        val (trueOverrides, falseOverrides) = overrides.groupBy { it.predicate().customModelData?.takeUnless { it == 0f } }.let { grouped ->
                            when {
                                itemKey.asString().endsWith("bow") ->
                                    grouped.values.flatMap { it.filter { p-> p.pulling } } to grouped.values.flatMap { it.filterNot { p -> p.pulling } }

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

                            // If the overrides contain any pull, we make a bow-type model
                            val finalModel = overrides.drop(1).mapNotNull {
                                RangeDispatchItemModel.Entry.entry(it.predicate().pull ?: return@mapNotNull null, ItemModel.reference(it.model()))
                            }.takeUnless { it.isEmpty() }?.let { pullingEntries ->
                                val property = if (itemKey.asString().contains("crossbow")) ItemNumericProperty.crossbowPull() else ItemNumericProperty.useDuration()
                                ItemModel.rangeDispatch(property, 0.05f, pullingEntries, baseOverrideModel)
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

                        val onTrue = ItemModel.rangeDispatch(ItemNumericProperty.customModelData(), 1f, onTrueCmdEntries, baseItemModel.onTrue())
                        val onFalse = ItemModel.rangeDispatch(ItemNumericProperty.customModelData(), 1f, onFalseCmdEntries, baseItemModel.onFalse())

                        ItemModel.conditional(baseItemModel.condition(), onTrue, onFalse)
                    }

                    is ReferenceItemModel -> ItemModel.rangeDispatch().fallback(baseItemModel).property(ItemNumericProperty.customModelData()).also { builder ->
                        builder.addEntries(overrides.mapNotNull { override ->
                            val cmd = override.predicate().customModelData ?: return@mapNotNull null
                            RangeDispatchItemModel.Entry.entry(cmd, ItemModel.reference(override.model(), baseItemModel.tints()))
                        })
                    }.build()

                    is SpecialItemModel -> {
                        val defaultDisplay = DisplayProperties.fromModel(model)
                        val newBase = ItemModel.special(baseItemModel.render(), when {
                            model.display() != defaultDisplay -> model.key()
                            else -> baseItemModel.base()
                        })

                        if (overrides.isNotEmpty()) ItemModel.rangeDispatch().fallback(newBase).property(ItemNumericProperty.customModelData()).apply {
                            addEntries(overrides.mapNotNull { override ->
                                val cmd = override.predicate().customModelData ?: return@mapNotNull null
                                RangeDispatchItemModel.Entry.entry(cmd, ItemModel.special(baseItemModel.render(), override.model()))
                            })
                        }.build()
                        else newBase
                    }
                    else -> baseItemModel
                }
            } ?: modelObject(standardItem?.model(), overrides, itemKey)

            resourcePack.item(Item.item(itemKey, finalNewItemModel))
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

    val ItemModel.isSimpleItemModel: Boolean get() {
        return (this as? ReferenceItemModel)?.tints()?.isEmpty() == true || this is EmptyItemModel
    }
}