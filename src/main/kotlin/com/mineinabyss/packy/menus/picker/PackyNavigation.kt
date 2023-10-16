package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mineinabyss.guiy.components.canvases.Chest
import com.mineinabyss.guiy.inventory.GuiyOwner
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.height
import com.mineinabyss.guiy.navigation.Navigator
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyServer
import org.bukkit.entity.Player

sealed class PackyScreen(val title: String, val height: Int) {
    data object Default : PackyScreen(packy.config.menu.title, packy.config.menu.height)

}

class PackySubScreen(val subMenu: PackyConfig.PackySubMenu) : PackyScreen(subMenu.title, subMenu.height)

typealias PackyNav = Navigator<PackyScreen>

class PackyUIScope(val player: Player) {
    var hasChanged = false
    val nav = PackyNav { PackyScreen.Default }
}

@Composable
fun GuiyOwner.PackyMainMenu(player: Player) {
    val scope = remember { PackyUIScope(player) }
    scope.apply {
        nav.withScreen(setOf(player), onEmpty = ::exit) { screen ->
            Chest(setOf(player), screen.title, Modifier.height(screen.height), onClose = {
                player.closeInventory()
                if (hasChanged) PackyServer.sendPack(player)
            }) {
                when (screen) {
                    PackyScreen.Default -> PackyMenu()
                    is PackySubScreen -> PackySubMenu(screen.subMenu.packs)
                }
            }
        }
    }
}
