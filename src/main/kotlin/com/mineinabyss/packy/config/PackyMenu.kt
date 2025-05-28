package com.mineinabyss.packy.config

import com.mineinabyss.guiy.modifiers.Modifier
import com.mineinabyss.guiy.modifiers.placement.absolute.at
import com.mineinabyss.guiy.modifiers.size
import com.mineinabyss.idofront.serialization.SerializableItemStack
import com.mineinabyss.idofront.serialization.toSerializable
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

@Serializable
data class PackyMenu(
    val title: String = "Packy Pack Picker",
    val height: Int = 6,
    val subMenus: Map<String, PackySubMenu> = mapOf()
) {
    enum class SubMenuType {
        MENU, CYCLING
    }

    @Serializable
    data class PackySubMenu(
        val title: String = "Packy SubMenu",
        val height: Int = 6,
        @EncodeDefault(NEVER) val button: SerializableItemStack = ItemStack(Material.PAPER).toSerializable(),
        val modifiers: Modifiers = Modifiers(),
        val type: SubMenuType = SubMenuType.MENU,
        val packs: Map<String, PackyPack> = mapOf()
    ) {
        fun refreshItem(itemStack: ItemStack, state: Boolean) {
            val cmd = CustomModelData.customModelData().addFlag(state).build()
            itemStack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, cmd)
        }

        fun refreshItem(itemStack: ItemStack, index: Int) {
            val cmd = CustomModelData.customModelData().addFloat(index.toFloat()).build()
            itemStack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, cmd)
        }
    }

    @Serializable
    data class PackyPack(
        @EncodeDefault(NEVER) val button: SerializableItemStack? = null,
        @EncodeDefault(NEVER) val modifiers: Modifiers = Modifiers()
    )

    @Serializable
    data class Modifiers(val offset: Offset = Offset(), val size: Size = Size()) {
        fun toModifier(): Modifier = Modifier.at(offset.x, offset.y).size(size.width, size.height)
    }

    @Serializable
    data class Offset(val x: Int = 0, val y: Int = 0) {
        fun toAtModifier(modifier: Modifier = Modifier): Modifier = modifier.at(x, y)
    }

    @Serializable
    data class Size(val width: Int = 1, val height: Int = 1) {
        fun toSizeModifier(modifier: Modifier = Modifier): Modifier = modifier.size(width, height)
    }
}