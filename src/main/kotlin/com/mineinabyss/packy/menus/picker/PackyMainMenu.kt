package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.*
import com.mineinabyss.guiy.components.Grid
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.clickable
import com.mineinabyss.idofront.items.editItemMeta
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.rotatedLeft
import com.mineinabyss.packy.menus.Button
import org.bukkit.Material
import org.bukkit.inventory.ItemStack


@Composable
fun PackyMenu() {
    val packyData = PackyDataProvider.current
    val scope = PackyScopeProvider.current
    val player = scope.player
    val subPackList = packy.config.menu.subMenus.map { it.value to it.value.packs.toList() }.toMap()
    packy.config.menu.subMenus.values.map { subMenu ->
        var packs by remember { mutableStateOf(subPackList[subMenu]!!) }

        if (subMenu.packs.size == 1) {
            val templateId = subMenu.packs.keys.firstOrNull() ?: return@map
            packy.templates.entries.find { it.key == templateId }?.value ?: return@map

            Item(subMenu.button.toItemStack(), subMenu.modifiers.toModifier().clickable {
                // Return if the task returns null, meaning button was spammed whilst a set was currently generating
                when {
                    templateId !in packyData.enabledPackIds -> PackPicker.addPack(player, templateId, packyData)
                    else -> PackPicker.removePack(player, templateId, packyData)
                } ?: return@clickable

                scope.hasChanged = true
                scope.nav.refresh()
            })
        } else when (subMenu.type) {
            PackyConfig.SubMenuType.MENU -> Item(
                subMenu.button.toItemStack(),
                subMenu.modifiers.toModifier().clickable { scope.nav.open(PackySubScreen(subMenu)) }
            )

            PackyConfig.SubMenuType.CYCLING -> {
                val templateId = packyData.enabledPackAddons.firstOrNull { it.id in subMenu.packs.keys }?.id ?: packs.first().first
                val pack = subMenu.packs[templateId] ?: return
                val currentTemplateIndex = packs.indexOf(templateId to pack)
                val nextTemplateId = packs[(currentTemplateIndex + 1) % packs.size].first

                CycleButton(subMenu, pack) {
                    // Return if the task returns null, meaning button was spammed whilst a set was currently generating
                    PackPicker.addPack(player, nextTemplateId, packyData) ?: return@CycleButton

                    packs = packs.rotatedLeft()
                    scope.hasChanged = true
                    scope.nav.refresh()
                }
            }
        }
    }
}

@Composable
fun CycleButton(subMenu: PackyConfig.PackySubMenu, pack: PackyConfig.PackyPack, onClick: () -> Unit) {
    val modifier = subMenu.modifiers.offset.toAtModifier()
    Grid(subMenu.modifiers.size.toSizeModifier(modifier)) {
        Button(enabled = true, onClick = onClick) {
            Item(subMenu.button.toItemStack(pack.button?.toItemStackOrNull() ?: ItemStack.empty()), subMenu.modifiers.size.toSizeModifier())
        }
    }

}


@Composable
fun BackButton(modifier: Modifier = Modifier) {
    val scope = PackyScopeProvider.current
    Button(onClick = { scope.nav.back() }, modifier = modifier) {
        Item(ItemStack(Material.PAPER).editItemMeta {
            displayName("<red><b>Back".miniMsg())
            setCustomModelData(1)
        })
    }
}
