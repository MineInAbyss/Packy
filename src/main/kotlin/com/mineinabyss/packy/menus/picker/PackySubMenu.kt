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
fun PackyUIScope.PackySubMenu(packyPacks: Map<String, PackyConfig.PackyPack>) {
    packyPacks.forEach { (templateId, pack) ->
        val template = packy.templates.find { it.id == templateId } ?: return@forEach
        Button(pack.modifiers.offset.toAtModifier(),
            onClick = {
                when {
                    template !in player.packyData.enabledPackAddons -> PackPicker.addPack(player, templateId, player)
                    else -> PackPicker.removePack(player, templateId, player)
                }
                hasChanged = true
                nav.back()
            }
        ) {
            Item(pack.button.toItemStack(), pack.modifiers.size.toSizeModifier())
        }
    }

    BackButton(Modifier.at(4, 5))
}
