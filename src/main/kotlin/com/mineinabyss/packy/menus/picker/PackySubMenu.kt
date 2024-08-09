package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.Composable
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.placement.absolute.at
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
        val template = packy.templates[templateId]?.id ?: return@forEach
        Button(pack.modifiers.toModifier(), onClick = {
            when {
                template !in player.packyData.templates -> PackPicker.enablePack(scope, player, packyData, templateId)
                else -> PackPicker.disablePack(scope, player, packyData, templateId)
            }
            scope.nav.back()
        }
        ) { Item((pack.button ?: subMenu.button).toItemStack(subMenu.button.toItemStack()), pack.modifiers.toModifier()) }
    }

    BackButton(Modifier.at(4, 5))
}
