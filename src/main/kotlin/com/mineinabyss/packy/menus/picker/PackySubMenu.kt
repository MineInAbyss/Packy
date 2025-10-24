package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.components.canvases.Chest
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.height
import com.mineinabyss.guiy.modifiers.placement.absolute.at
import com.mineinabyss.guiy.modifiers.size
import com.mineinabyss.guiy.navigation.LocalBackGestureDispatcher
import com.mineinabyss.guiy.viewmodel.viewModel
import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyMenu
import com.mineinabyss.packy.menus.Button
import io.papermc.paper.datacomponent.DataComponentTypes

/**
 * A child menu opened from the main menu which lets users select one item from a list of options.
 * Essentially like a dropdown for selecting one of n templates, but in a separate screen.
 */
@Composable
fun PackySubMenu(
    subMenu: PackyMenu.PackySubMenu,
    packPickerViewModel: PackPickerViewModel = viewModel(),
) = Chest(subMenu.title, Modifier.height(subMenu.height)) {
    val backGesture = LocalBackGestureDispatcher.current
    subMenu.packs.forEach { (templateId, pack) ->
        Button(pack.modifiers.toModifier(), onClick = {
            packPickerViewModel.togglePack(templateId)
            backGesture.onBack()
        }) {
            val serializable = pack.button ?: subMenu.button
            val applyTo by remember(templateId, subMenu) { packPickerViewModel.itemFor(templateId, subMenu) }.collectAsState()
            val item = serializable.toItemStack(applyTo)
            Item(item, pack.modifiers.toModifier())
            if (subMenu.allSlotsEmptyExceptFirst) {
                Item(item.clone(), pack.modifiers.toModifier().size(1))
                item.setData(DataComponentTypes.ITEM_MODEL, ResourcePacks.EMPTY_MODEL)
            }
        }
    }

    BackButton(Modifier.at(4, 5))
}
