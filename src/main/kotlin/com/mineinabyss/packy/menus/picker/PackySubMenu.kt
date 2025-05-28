package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.Composable
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.placement.absolute.at
import com.mineinabyss.idofront.serialization.SerializableDataTypes
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.PackyMenu
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.menus.Button
import io.papermc.paper.datacomponent.DataComponentTypes

@Composable
fun PackySubMenu(subMenu: PackyMenu.PackySubMenu) {
    val packyData = PackyDataProvider.current
    val scope = PackyScopeProvider.current
    val player = scope.player
    subMenu.packs.forEach { (templateId, pack) ->
        val template = packy.templates[templateId]?.id ?: return@forEach
        Button(pack.modifiers.toModifier(), onClick = {
            val newState = template !in player.packyData.templates
            if (newState) PackPicker.enablePack(scope, player, packyData, templateId)
            else PackPicker.disablePack(scope, player, packyData, templateId)

            scope.nav.back()
        }) {
            val serializable = pack.button ?: subMenu.button
            val applyTo = subMenu.button.toItemStack()
            subMenu.refreshItem(applyTo, templateId in packyData.enabledPackIds)
            Item(serializable.toItemStack(applyTo), pack.modifiers.toModifier())
        }
    }

    BackButton(Modifier.at(4, 5))
}
