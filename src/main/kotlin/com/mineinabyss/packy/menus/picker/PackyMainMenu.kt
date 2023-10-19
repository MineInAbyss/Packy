package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.*
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.clickable
import com.mineinabyss.idofront.items.editItemMeta
import com.mineinabyss.idofront.messaging.broadcast
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.idofront.textcomponents.serialize
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.menus.Button
import org.bukkit.Material
import org.bukkit.inventory.ItemStack


@Composable
fun PackyUIScope.PackyMenu() {
    val subPackList = packy.config.menu.subMenus.map { it.value to it.value.packs.toList() }.toMap()
    packy.config.menu.subMenus.values.map { subMenu ->
        var packs by remember { mutableStateOf(subPackList[subMenu]!!.toMutableList()) }

        if (subMenu.packs.size == 1) {
            val templateId = subMenu.packs.keys.first()
            val template = packy.templates.entries.find { it.key == templateId }?.value ?: return

            Item(subMenu.button.toItemStack(), subMenu.modifiers.toModifier().clickable {
                // Return if the task returns null, meaning button was spammed whilst a set was currently generating
                when {
                    template !in player.packyData.enabledPackAddons -> PackPicker.addPack(player, templateId)
                    else -> PackPicker.removePack(player, templateId)
                } ?: return@clickable

                packs = packs.apply { add(removeFirst()) }
                hasChanged = true
                nav.refresh()
            })
        } else when (subMenu.type) {
            PackyConfig.SubMenuType.MENU -> Item(
                subMenu.button.toItemStack(),
                subMenu.modifiers.toModifier().clickable { nav.open(PackySubScreen(subMenu)) }
            )

            PackyConfig.SubMenuType.CYCLING -> {
                val (templateId, pack) = packs.first().first to packs[1].second

                ItemButton(subMenu, pack) {
                    val template = packy.templates.entries.find { it.key == templateId }?.value ?: return@ItemButton
                    // Return if the task returns null, meaning button was spammed whilst a set was currently generating
                    when {
                        template !in player.packyData.enabledPackAddons -> PackPicker.addPack(player, templateId)
                        else -> PackPicker.removePack(player, templateId)
                    } ?: return@ItemButton

                    packs = packs.apply { add(removeFirst()) }
                    hasChanged = true
                    nav.refresh()
                }
            }
        }
    }
}

@Composable
fun ItemButton(subMenu: PackyConfig.PackySubMenu, pack: PackyConfig.PackyPack, onClick: () -> Unit) {
    Button(enabled = true, onClick = onClick) {
        Item((pack.button ?: subMenu.button).toItemStack(), subMenu.modifiers.toModifier())
    }
}


@Composable
fun PackyUIScope.BackButton(modifier: Modifier = Modifier) {
    Button(onClick = { nav.back() }, modifier = modifier) {
        Item(ItemStack(Material.PAPER).editItemMeta {
            displayName("<red><b>Back".miniMsg())
            setCustomModelData(1)
        })
    }
}
