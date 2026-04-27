package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.Composable
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.components.VerticalGrid
import com.mineinabyss.guiy.components.button.Button
import com.mineinabyss.packy.config.PackyMenu
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.key.Key

@Composable
fun CycleButton(subMenu: PackyMenu.PackySubMenu, pack: PackyMenu.PackyPack, onClick: () -> Unit) {
    val modifier = subMenu.modifiers.offset.toAtModifier()
    val size = subMenu.modifiers.size
    val item = subMenu.buttonFor(pack, subMenu.packs.values.indexOf(pack).coerceAtLeast(0))

    VerticalGrid(subMenu.modifiers.size.toSizeModifier(modifier)) {
        Button(enabled = true, onClick = onClick) {
            Item(when {
                subMenu.allSlotsEmptyExceptFirst -> item.clone()
                    .apply { setData(DataComponentTypes.ITEM_MODEL, Key.key("minecraft:empty")) }
                else -> item
            }, size.toSizeModifier())
        }
    }
    if (subMenu.allSlotsEmptyExceptFirst) Button(enabled = true, onClick = onClick) {
        Item(item, modifier)
    }
}