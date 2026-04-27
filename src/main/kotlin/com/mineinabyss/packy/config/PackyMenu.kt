package com.mineinabyss.packy.config

import androidx.compose.ui.unit.dp
import com.mineinabyss.idofront.serialization.SerializableItemStack
import com.mineinabyss.idofront.serialization.toSerializable
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.CustomModelData
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.Serializable
import me.dvyy.compose.mini.layout.modifiers.offset
import me.dvyy.compose.mini.layout.modifiers.size
import me.dvyy.compose.mini.modifier.Modifier
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

@Serializable
data class PackyMenu(
    val title: String = "   Packy Pack Picker",
    val height: Int = 6,
    val subMenus: Map<String, PackySubMenu> = mapOf(),
) {
    enum class SubMenuType {
        MENU, CYCLING
    }

    /**
     * Describes a customization button displayed in the packy menu.
     *
     * - If [type] is `CYCLING`, will switch between different display items on click.
     * - If it is `MENU`, will open a submenu on click and let the user select a pack.
     */
    @Serializable
    data class PackySubMenu(
        val title: String = "Packy SubMenu",
        val height: Int = 6,
        @EncodeDefault(NEVER) val button: SerializableItemStack = ItemStack(Material.PAPER).toSerializable(),
        val modifiers: Modifiers = Modifiers(),
        val type: SubMenuType = SubMenuType.MENU,
        val allSlotsEmptyExceptFirst: Boolean = false,
        val packs: Map<String, PackyPack> = mapOf(),
    ) {
        fun buttonFor(state: Boolean): ItemStack {
            val item = button.toItemStack()
            val cmd = CustomModelData.customModelData().addFlag(state).build()
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, cmd)
            return item
        }

        fun buttonFor(parent: PackyPack, index: Int): ItemStack {
            val item = button.toItemStack(parent.button?.toItemStackOrNull() ?: ItemStack.empty())
            val cmd = CustomModelData.customModelData().addFloat(index.toFloat()).build()
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, cmd)
            return item
        }
    }

    @Serializable
    data class PackyPack(
        @EncodeDefault(NEVER) val button: SerializableItemStack? = null,
        @EncodeDefault(NEVER) val modifiers: Modifiers = Modifiers(),
    )

    @Serializable
    data class Modifiers(val offset: Offset = Offset(), val size: Size = Size()) {
        fun toModifier(): Modifier = Modifier.offset(offset.x.dp, offset.y.dp).size(size.width.dp, size.height.dp)
    }

    @Serializable
    data class Offset(val x: Int = 0, val y: Int = 0) {
        fun toAtModifier(modifier: Modifier = Modifier): Modifier = modifier.offset(x.dp, y.dp)
    }

    @Serializable
    data class Size(val width: Int = 1, val height: Int = 1) {
        fun toSizeModifier(modifier: Modifier = Modifier): Modifier = modifier.size(width.dp, height.dp)
    }
}