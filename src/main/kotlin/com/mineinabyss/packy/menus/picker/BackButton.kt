package com.mineinabyss.packy.menus.picker

import androidx.compose.runtime.Composable
import com.mineinabyss.guiy.components.Item
import com.mineinabyss.guiy.components.button.Button
import com.mineinabyss.guiy.navigation.LocalBackGestureDispatcher
import com.mineinabyss.idofront.serialization.SerializableDataTypes
import com.mineinabyss.idofront.serialization.SerializableItemStack
import com.mineinabyss.idofront.textcomponents.miniMsg
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import me.dvyy.compose.mini.modifier.Modifier
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

@Composable
internal fun BackButton(modifier: Modifier = Modifier) {
    val backGesture = LocalBackGestureDispatcher.current
    Button(onClick = { backGesture.onBack() }, modifier = modifier) {
        SerializableItemStack(
            _customName = "<red><b>Back",
            customModelData = SerializableDataTypes.CustomModelData(floats = listOf(1f))
        )
        Item(ItemStack(Material.PAPER).apply {
            setData(DataComponentTypes.CUSTOM_NAME, "<red><b>Back".miniMsg())
            setData(
                DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addFloat(1f).build()
            )
        })
    }
}