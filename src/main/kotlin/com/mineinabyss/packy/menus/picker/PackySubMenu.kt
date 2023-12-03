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
fun PackySubMenu(subMenu: PackyConfig.PackySubMenu) {
    val packyData = PackyDataProvider.current
    val scope = PackyScopeProvider.current
    val player = scope.player
    subMenu.packs.forEach { (templateId, pack) ->
        val template = packy.templates.entries.find { it.key == templateId }?.value ?: return@forEach
        Button(pack.modifiers.toModifier(), onClick = {
            when {
                template !in player.packyData.enabledPackAddons -> PackPicker.addPack(player, templateId, packyData)
                else -> PackPicker.removePack(player, templateId, packyData)
            }
            scope.hasChanged = true
            scope.nav.back()
        }
        ) { Item((pack.button ?: subMenu.button).toItemStack(subMenu.button.toItemStack()), pack.modifiers.toModifier()) }
    }

    BackButton(Modifier.at(4, 5))
}
