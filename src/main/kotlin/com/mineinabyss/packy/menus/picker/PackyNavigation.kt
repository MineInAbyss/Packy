package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.Composable
import com.mineinabyss.guiy.canvas.CurrentPlayer
import com.mineinabyss.guiy.navigation.NavHost
import com.mineinabyss.guiy.navigation.composable
import com.mineinabyss.guiy.navigation.rememberNavController
import com.mineinabyss.guiy.viewmodel.viewModel
import com.mineinabyss.packy.config.PackyMenu

sealed interface PackyScreen {
    data object Default : PackyScreen

    data class PackySubScreen(val subMenu: PackyMenu.PackySubMenu) : PackyScreen
}

@Composable
fun PackyMenu() {
    val player = CurrentPlayer
    viewModel { PackPickerViewModel(player) }
    val navController = rememberNavController()
    NavHost(navController, startDestination = PackyScreen.Default) {
        composable<PackyScreen.Default> {
            PackyMainMenu(onNavigateToSubMenu = {
                navController.navigate(PackyScreen.PackySubScreen(it))
            })
        }
        composable<PackyScreen.PackySubScreen> {
            PackySubMenu(it.subMenu)
        }
    }
}
