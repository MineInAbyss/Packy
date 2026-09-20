package com.mineinabyss.packy.helpers.atlas

import net.kyori.adventure.key.Key
import team.unnamed.creative.blockstate.BlockState
import team.unnamed.creative.item.CompositeItemModel
import team.unnamed.creative.item.ConditionItemModel
import team.unnamed.creative.item.ItemModel
import team.unnamed.creative.item.RangeDispatchItemModel
import team.unnamed.creative.item.ReferenceItemModel
import team.unnamed.creative.item.SelectItemModel

/** Every model an item-definition can end up rendering, across all of its branches */
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

internal fun BlockState.referencedModels(): Set<Key> = buildSet {
    variants().values.forEach { multiVariant -> multiVariant.variants().forEach { add(it.model()) } }
    multipart().forEach { selector -> selector.variant().variants().forEach { add(it.model()) } }
}
