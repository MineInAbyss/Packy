package com.mineinabyss.packy.helpers.atlas

import net.kyori.adventure.key.Key
import team.unnamed.creative.blockstate.BlockState
import team.unnamed.creative.blockstate.MultiVariant
import team.unnamed.creative.blockstate.Selector
import team.unnamed.creative.item.CompositeItemModel
import team.unnamed.creative.item.ConditionItemModel
import team.unnamed.creative.item.Item
import team.unnamed.creative.item.ItemModel
import team.unnamed.creative.item.RangeDispatchItemModel
import team.unnamed.creative.item.ReferenceItemModel
import team.unnamed.creative.item.SelectItemModel

internal fun ItemModel.referencedModels(): Set<Key> = buildSet {
    fun walk(itemModel: ItemModel) {
        when (itemModel) {
            is ReferenceItemModel -> add(itemModel.model())
            is RangeDispatchItemModel -> {
                itemModel.entries().forEach { walk(it.model()) }
                itemModel.fallback()?.let(::walk)
            }
            is SelectItemModel -> {
                itemModel.cases().forEach { walk(it.model()) }
                itemModel.fallback()?.let(::walk)
            }
            is ConditionItemModel -> {
                walk(itemModel.onTrue())
                walk(itemModel.onFalse())
            }
            is CompositeItemModel -> itemModel.models().forEach(::walk)
            // SpecialItemModel base-models only provide display-transforms, no atlas-textures
            else -> {}
        }
    }
    walk(this@referencedModels)
}

internal fun Item.remapModels(remap: Map<Key, Key>): Item {
    fun walk(itemModel: ItemModel): ItemModel = when (itemModel) {
        is ReferenceItemModel -> ItemModel.reference(remap[itemModel.model()] ?: itemModel.model(), itemModel.tints(), itemModel.transformation())
        is RangeDispatchItemModel -> ItemModel.rangeDispatch(
            itemModel.property(), itemModel.scale(),
            itemModel.entries().map { RangeDispatchItemModel.Entry.entry(it.threshold(), walk(it.model())) },
            itemModel.fallback()?.let(::walk), itemModel.transformation()
        )
        is SelectItemModel -> ItemModel.select(
            itemModel.property(),
            itemModel.cases().map { SelectItemModel.Case._case(walk(it.model()), it.`when`()) },
            itemModel.fallback()?.let(::walk), itemModel.transformation()
        )
        is ConditionItemModel -> ItemModel.conditional(itemModel.condition(), walk(itemModel.onTrue()), walk(itemModel.onFalse()), itemModel.transformation())
        is CompositeItemModel -> ItemModel.composite(itemModel.models().map(::walk), itemModel.transformation())
        else -> itemModel
    }
    return Item.item(key(), walk(model()), handAnimationOnSwap(), oversizedInGui(), swapAnimationScale())
}

internal fun BlockState.referencedModels(): Set<Key> = buildSet {
    variants().values.forEach { multiVariant -> multiVariant.variants().forEach { add(it.model()) } }
    multipart().forEach { selector -> selector.variant().variants().forEach { add(it.model()) } }
}

internal fun BlockState.remapModels(remap: Map<Key, Key>): BlockState {
    fun MultiVariant.remap() = MultiVariant.of(variants().map { variant ->
        remap[variant.model()]?.let { variant.toBuilder().model(it).build() } ?: variant
    })

    return BlockState.of(
        key(),
        variants().mapValues { (_, multiVariant) -> multiVariant.remap() },
        multipart().map { Selector.of(it.condition(), it.variant().remap()) },
    )
}
