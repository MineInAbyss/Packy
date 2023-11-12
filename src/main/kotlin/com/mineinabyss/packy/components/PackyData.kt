package com.mineinabyss.packy.components

import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.conflictsWith
import com.mineinabyss.packy.config.packy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.entity.Player

@Serializable
@SerialName("packy:packy_data")
data class PackyData(val enabledPackAddons: MutableSet<PackyTemplate> = packy.templates.values.filter { !it.forced && it.default }.toMutableSet(), var bypassForced: Boolean = false) {
    val enabledPackIds get() = enabledPackAddons.map { it.id }.toSet()
}
var Player.packyData
    get() = this.toGeary().get<PackyData>() ?: PackyData().let { this.toGeary().setPersisting(it) }
    set(value) {
        this.toGeary().setPersisting(value)
    }
fun Player.removeConflictingPacks(template: PackyTemplate) : Set<PackyTemplate> {
    return mutableSetOf<PackyTemplate>().apply {
        packyData.enabledPackAddons.removeIf { t ->
            t.conflictsWith(template).let { if (it) this.add(t); it }
        }
    }.toSet()
}
