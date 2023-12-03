package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.*
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.mineinabyss.guiy.components.canvases.Chest
import com.mineinabyss.guiy.inventory.GuiyOwner
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.height
import com.mineinabyss.guiy.navigation.Navigator
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyServer
import kotlinx.coroutines.withContext
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
    var packyData: PackyData by remember { mutableStateOf(PackyData(mutableSetOf())) }
    LaunchedEffect(Unit) {
        withContext(packy.plugin.minecraftDispatcher) {
            packyData = player.packyData
        }
    }
    CompositionLocalProvider(PackyScopeProvider provides scope, PackyDataProvider provides packyData) {
        scope.nav.withScreen(setOf(player), onEmpty = ::exit) { screen ->
            Chest(setOf(player), screen.title, Modifier.height(screen.height), onClose = {
                player.closeInventory()
                if (scope.hasChanged) packy.plugin.launch {
                    PackyServer.sendPack(player)
                }
            }) {
                when (screen) {
                    PackyScreen.Default -> PackyMenu()
                    is PackySubScreen -> PackySubMenu(screen.subMenu)
                }
            }
        }
    }
}

val PackyScopeProvider = compositionLocalOf<PackyUIScope> { error("No packy scope provided") }
val PackyDataProvider = compositionLocalOf<PackyData> { error("No packy data provided") }
