package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.Composable
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.inventory.guiy
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.at
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.menus.Button

@Composable
fun PackyUIScope.PackySubMenu(subMenu: PackyConfig.PackySubMenu) {
    subMenu.packs.forEach { (templateId, pack) ->
        val template = packy.templates.entries.find { it.key == templateId }?.value ?: return@forEach
        Button(onClick = {
            when {
                template !in player.packyData.enabledPackAddons -> PackPicker.addPack(player, templateId)
                else -> PackPicker.removePack(player, templateId)
            }
            hasChanged = true
            nav.back()
        }
        ) { Item((pack.button ?: subMenu.button).toItemStack(), pack.modifiers.toModifier()) }
    }

    BackButton(Modifier.at(4, 5))
}
