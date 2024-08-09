package com.mineinabyss.packy.components

import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.serialization.getOrSetPersisting
import com.mineinabyss.geary.serialization.setPersisting
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.entity.Player
import java.util.SortedMap

@Serializable
@SerialName("packy:packy_data")
data class PackyData(
    val templates: MutableMap<String, Boolean> = packy.templates.associate { it.id to (it.default || it.required) }.toMutableMap(),
    var bypassForced: Boolean = false
) {
    val enabledPackIds get() = templates.filterValues { it }.keys.toSortedSet()
}

var Player.packyData
    get() = this.toGeary().getOrSetPersisting<PackyData> { PackyData() }
    set(value) { this.toGeary().setPersisting(value) }
