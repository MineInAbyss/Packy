package com.mineinabyss.packy.helpers

import com.mineinabyss.geary.systems.GearyListener
import com.mineinabyss.geary.systems.accessors.Pointers
import com.mineinabyss.packy.components.PackyData
import org.bukkit.entity.Player

class PackyAddDataTracker() : GearyListener() {
    val Pointers.data by get<PackyData>().whenSetOnTarget()
    val Pointers.player by get<Player>().on(target)
    override fun Pointers.handle() {
        PackyServer.playerToDataMap[player] = data
    }
}
