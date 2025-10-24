package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.Composable
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.components.VerticalGrid
import com.mineinabyss.guiy.components.items.LocalItemTheme
import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.packy.config.PackyMenu
import com.mineinabyss.packy.menus.Button
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.inventory.ItemStack

@Composable
fun CycleButton(subMenu: PackyMenu.PackySubMenu, pack: PackyMenu.PackyPack, onClick: () -> Unit) {
    val modifier = subMenu.modifiers.offset.toAtModifier()
    val size = subMenu.modifiers.size
    val item = subMenu.buttonFor(pack, subMenu.packs.values.indexOf(pack).coerceAtLeast(0))

    VerticalGrid(subMenu.modifiers.size.toSizeModifier(modifier)) {
        Button(enabled = true, onClick = onClick) {
            val emptyItem = LocalItemTheme.current.invisible
            Item(if (subMenu.allSlotsEmptyExceptFirst) emptyItem else item, size.toSizeModifier())
        }
    }
    if (subMenu.allSlotsEmptyExceptFirst) Button(enabled = true, onClick = onClick) {
        Item(item, modifier)
    }
}