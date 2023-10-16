package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.components.canvases.Chest
import com.mineinabyss.guiy.guiyPlugin
import com.mineinabyss.guiy.inventory.GuiyOwner
import com.mineinabyss.guiy.inventory.guiy
import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.at
import com.mineinabyss.guiy.modifiers.clickable
import com.mineinabyss.guiy.modifiers.height
import com.mineinabyss.guiy.navigation.Navigator
import com.mineinabyss.idofront.items.editItemMeta
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyServer
import com.mineinabyss.packy.menus.Button
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

//class PackyUIScope(val player: Player) {
//    var hasChanged = false
//}

@Composable
fun PackyUIScope.PackyMenu() {
    packy.config.menu.subMenus.values.map { subMenu ->
        Item(subMenu.button.toItemStack(), subMenu.modifiers.toModifier().clickable {
            nav.open(PackySubScreen(subMenu))
        })
    }
}


@Composable
fun PackyUIScope.BackButton(modifier: Modifier = Modifier) {
    Button(onClick = { nav.back() }, modifier = modifier) {
        Item(ItemStack(Material.PAPER).editItemMeta {
            displayName("<red><b>Back".miniMsg())
            setCustomModelData(1)
        })
    }
}
