package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.*
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.mineinabyss.guiy.components.canvases.Chest
import com.mineinabyss.guiy.inventory.LocalGuiyOwner
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.height
import com.mineinabyss.guiy.navigation.Navigator
import com.mineinabyss.packy.components.PackyData
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.PackyServer
import com.mineinabyss.packy.config.PackyMenu
import kotlinx.coroutines.withContext
import org.bukkit.entity.Player

sealed class PackyScreen(val title: String, val height: Int) {
    data object Default : PackyScreen(packy.menu.title, packy.menu.height)

}

class PackySubScreen(val subMenu: PackyMenu.PackySubMenu) : PackyScreen(subMenu.title, subMenu.height)

typealias PackyNav = Navigator<PackyScreen>

class PackyUIScope(val player: Player) {
    var changedAction: (() -> Unit?)? = null
    val nav = PackyNav { PackyScreen.Default }
}

@Composable
fun PackyMainMenu(player: Player) {
    val owner = LocalGuiyOwner.current
    val scope = remember { PackyUIScope(player) }
    val originalTemplates = player.packyData.templates.toMap()
    var packyData: PackyData by remember { mutableStateOf(PackyData(mutableMapOf())) }
    LaunchedEffect(Unit) {
        withContext(packy.plugin.minecraftDispatcher) {
            packyData = player.packyData
        }
    }
    CompositionLocalProvider(PackyScopeProvider provides scope, PackyDataProvider provides packyData) {
        scope.nav.withScreen(setOf(player), onEmpty = owner::exit) { screen ->
            Chest(setOf(player), screen.title, Modifier.height(screen.height), onClose = {
                owner.exit()
                if (originalTemplates != packyData.templates) scope.changedAction?.invoke()?.run {
                    packy.plugin.launch {
                        player.packyData = packyData
                        PackyServer.sendPack(player)
                    }
                }
            }) {
                when (screen) {
                    PackyScreen.Default -> PackyMainMenu()
                    is PackySubScreen -> PackySubMenu(screen.subMenu)
                }
            }
        }
    }
}

val PackyScopeProvider = compositionLocalOf<PackyUIScope> { error("No packy scope provided") }
val PackyDataProvider = compositionLocalOf<PackyData> { error("No packy data provided") }
