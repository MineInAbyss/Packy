package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.components.canvases.Chest
import com.mineinabyss.guiy.modifiers.click.clickable
import com.mineinabyss.guiy.viewmodel.viewModel
import com.mineinabyss.packy.config.PackyMenu
import com.mineinabyss.packy.config.PackyMenu.SubMenuType.CYCLING
import com.mineinabyss.packy.config.PackyMenu.SubMenuType.MENU
import com.mineinabyss.packy.config.packy
import me.dvyy.compose.mini.layout.modifiers.height
import me.dvyy.compose.mini.modifier.Modifier

@Composable
fun PackyMainMenu(
    onNavigateToSubMenu: (PackyMenu.PackySubMenu) -> Unit,
    packPickerViewModel: PackPickerViewModel = viewModel(),
) = Chest(
    title = packy.menu.title,
    modifier = Modifier.height(packy.menu.height.dp),
    onClose = {
        packPickerViewModel.sendPackChanges()
        exit()
    }
) {
    val packyData by packPickerViewModel.packyData.collectAsState()
    packy.menu.subMenus.values.map { subMenu ->
        if (subMenu.packs.size == 1) {
            val templateId = subMenu.packs.keys.firstOrNull()?.takeIf { it in packy.templates } ?: return@map
            val itemStack = subMenu.buttonFor(templateId in packyData.enabledPackIds)
            Item(itemStack, subMenu.modifiers.toModifier().clickable {
                packPickerViewModel.togglePack(templateId)
            })
        } else when (subMenu.type) {
            MENU -> Item(
                subMenu.button.toItemStack(),
                subMenu.modifiers.toModifier().clickable { onNavigateToSubMenu(subMenu) }
            )

            CYCLING -> {
                val packs = packPickerViewModel.subPackList[subMenu]!!
                val templateId = packyData.enabledPackIds.firstOrNull(subMenu.packs.keys::contains) ?: packs.first().first
                val pack = subMenu.packs[templateId] ?: return@map
                var index by remember { mutableStateOf(packs.indexOf(templateId to pack)) }
                val nextTemplateId = packs[(index + 1) % packs.size].first

                CycleButton(subMenu, pack, onClick = {
                    packPickerViewModel.enablePack(nextTemplateId)
                    index = (index + 1) % packs.size
                })
            }
        }
    }
}
