package com.mineinabyss.packy.listener

import com.mineinabyss.idofront.plugin.Plugins
import com.ticxo.modelengine.api.events.ModelRegistrationEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

object TemplateLoadTriggers : Listener {

    init {
        if (Plugins.isEnabled("ModelEngine")) {
            @EventHandler
            fun ModelRegistrationEvent.onMegPackZipped() {

            }
        }
    }
}