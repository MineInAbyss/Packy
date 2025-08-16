package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.*
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.components.VerticalGrid
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.click.clickable
import com.mineinabyss.guiy.modifiers.fillMaxSize
import com.mineinabyss.guiy.modifiers.placement.absolute.at
import com.mineinabyss.guiy.modifiers.placement.offset.offset
import com.mineinabyss.guiy.modifiers.placement.padding.padding
import com.mineinabyss.guiy.modifiers.size
import com.mineinabyss.guiy.modifiers.sizeIn
import com.mineinabyss.idofront.items.editItemMeta
import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.idofront.serialization.SerializableDataTypes
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.PackyMenu
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.rotatedLeft
import com.mineinabyss.packy.menus.Button
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

@Composable
fun PackyMainMenu() {
    val packyData = PackyDataProvider.current
    val scope = PackyScopeProvider.current
    val player = scope.player
    val subPackList = packy.menu.subMenus.map { it.value to it.value.packs.toList() }.toMap()
    packy.menu.subMenus.values.map { subMenu ->
        var packs by remember { mutableStateOf(subPackList[subMenu]!!) }

        if (subMenu.packs.size == 1) {
            val templateId = subMenu.packs.keys.firstOrNull()?.takeIf { it in packy.templates } ?: return@map
            val itemStack = subMenu.button.toItemStack()
            subMenu.refreshItem(itemStack, templateId in packyData.enabledPackIds)
            Item(itemStack, subMenu.modifiers.toModifier().clickable {
                // Return if the task returns null, meaning button was spammed whilst a set was currently generating
                when {
                    templateId !in packyData.enabledPackIds -> PackPicker.enablePack(scope, player, packyData, templateId)
                    else -> PackPicker.disablePack(scope, player, packyData, templateId)
                } ?: return@clickable

                subMenu.refreshItem(itemStack, templateId in packyData.enabledPackIds)

                scope.nav.refresh()
            })
        } else when (subMenu.type) {
            PackyMenu.SubMenuType.MENU -> Item(
                subMenu.button.toItemStack(),
                subMenu.modifiers.toModifier().clickable { scope.nav.open(PackySubScreen(subMenu)) }
            )

            PackyMenu.SubMenuType.CYCLING -> {
                val templateId = packyData.enabledPackIds.firstOrNull(subMenu.packs.keys::contains) ?: packs.first().first
                val pack = subMenu.packs[templateId] ?: return
                val currentTemplateIndex = packs.indexOf(templateId to pack)
                val nextTemplateId = packs[(currentTemplateIndex + 1) % packs.size].first

                CycleButton(subMenu, pack) {
                    // Return if the task returns null, meaning button was spammed whilst a set was currently generating
                    PackPicker.enablePack(scope, player, packyData, nextTemplateId) ?: return@CycleButton

                    packs = packs.rotatedLeft()
                    scope.nav.refresh()
                }
            }
        }
    }
}

@Composable
fun CycleButton(subMenu: PackyMenu.PackySubMenu, pack: PackyMenu.PackyPack, onClick: () -> Unit) {
    val modifier = subMenu.modifiers.offset.toAtModifier()
    val size = subMenu.modifiers.size
    val item = subMenu.button.toItemStack(pack.button?.toItemStackOrNull() ?: ItemStack.empty())
    val emptyItem = item.clone().apply { setData(DataComponentTypes.ITEM_MODEL, ResourcePacks.EMPTY_MODEL) }
    subMenu.refreshItem(item, subMenu.packs.values.indexOf(pack).coerceAtLeast(0))

    VerticalGrid(subMenu.modifiers.size.toSizeModifier(modifier)) {
        Button(enabled = true, onClick = onClick) {
            Item(if (subMenu.allSlotsEmptyExceptFirst) emptyItem else item, size.toSizeModifier())
        }
    }
    if (subMenu.allSlotsEmptyExceptFirst) Button(enabled = true, onClick = onClick) {
        Item(item, modifier)
    }

}


@Composable
fun BackButton(modifier: Modifier = Modifier) {
    val scope = PackyScopeProvider.current
    Button(onClick = { scope.nav.back() }, modifier = modifier) {
        Item(ItemStack(Material.PAPER).apply {
            setData(DataComponentTypes.CUSTOM_NAME, "<red><b>Back".miniMsg())
            setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addFloat(1f).build())
        })
    }
}
