package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.components.button.Button
import com.mineinabyss.guiy.components.canvases.Chest
import com.mineinabyss.guiy.navigation.LocalBackGestureDispatcher
import com.mineinabyss.guiy.viewmodel.viewModel
import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.packy.config.PackyMenu
import io.papermc.paper.datacomponent.DataComponentTypes
import me.dvyy.compose.mini.layout.modifiers.height
import me.dvyy.compose.mini.layout.modifiers.offset
import me.dvyy.compose.mini.layout.modifiers.size
import me.dvyy.compose.mini.modifier.Modifier

/**
 * A child menu opened from the main menu which lets users select one item from a list of options.
 * Essentially like a dropdown for selecting one of n templates, but in a separate screen.
 */
@Composable
fun PackySubMenu(
    subMenu: PackyMenu.PackySubMenu,
    packPickerViewModel: PackPickerViewModel = viewModel(),
) = Chest(subMenu.title, Modifier.height(subMenu.height.dp)) {
    val backGesture = LocalBackGestureDispatcher.current
    subMenu.packs.forEach { (templateId, pack) ->
        Button(onClick = {
            packPickerViewModel.togglePack(templateId)
            backGesture.onBack()
        }, pack.modifiers.toModifier()) {
            val serializable = pack.button ?: subMenu.button
            val applyTo by remember(templateId, subMenu) { packPickerViewModel.itemFor(templateId, subMenu) }.collectAsState()
            val item = serializable.toItemStack(applyTo)
            Item(item, pack.modifiers.toModifier())
            if (subMenu.allSlotsEmptyExceptFirst) {
                Item(item.clone(), pack.modifiers.toModifier().size(1.dp))
                item.setData(DataComponentTypes.ITEM_MODEL, ResourcePacks.EMPTY_MODEL)
            }
        }
    }

    BackButton(Modifier.offset(4.dp, 5.dp))
}
