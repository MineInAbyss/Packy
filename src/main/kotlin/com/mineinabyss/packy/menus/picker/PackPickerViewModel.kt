package com.mineinabyss.packy.menus.picker

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.guiy.viewmodel.GuiyViewModel
import com.mineinabyss.idofront.messaging.info
import com.mineinabyss.packy.PackyServer
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.PackyMenu
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import kotlinx.coroutines.flow.*
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class PackPickerViewModel(
    val player: Player,
) : GuiyViewModel() {
    private val originalTemplates = player.packyData.templates.toMap()
    private val _packyData = MutableStateFlow(player.packyData)
    val packyData = _packyData.asStateFlow()
    val subPackList = packy.menu.subMenus.map { it.value to it.value.packs.toList() }.toMap()

    fun itemFor(
        templateId: String,
        subMenu: PackyMenu.PackySubMenu,
    ): StateFlow<ItemStack> = packyData.map {
        subMenu.buttonFor(state = it.enabledPackIds.contains(templateId))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ItemStack.empty())

    /**
     * Resends the resourcepack with any changes made by the player in the menu.
     */
    fun sendPackChanges() {
        val packyData = _packyData.value
        if (originalTemplates != packyData.templates) {
            // todo send message listing packs that were enabled/disabled
            val enabledPacks = packyData.templates.filter { it.value }
                .keys.filter { !originalTemplates[it]!! }
            val disabledPacks = packyData.templates.filter { !it.value }
                .keys.filter { originalTemplates[it]!! }

            player.info(buildString {
                appendLine("<gray>Changed resourcepacks:")
                if (enabledPacks.isNotEmpty())
                    append(" • Enabled <green>${enabledPacks.joinToString(", ")}</green>")
                if (disabledPacks.isNotEmpty()) {
                    if (enabledPacks.isNotEmpty()) appendLine()
                    append(" • Disabled <red>${disabledPacks.joinToString(", ")}</red>")
                }
            })

            packy.plugin.launch {
                player.packyData = packyData
                PackyServer.sendPack(player)
            }
        }
    }

    fun togglePack(templateId: String) {
        val isEnabled = _packyData.value.templates[templateId] == true
        if (isEnabled) disablePack(templateId)
        else enablePack(templateId)
    }

    fun enablePack(pack: String) {
        packy.templates[pack]?.let { template ->
            disableConflictingPacks(template)
            if (_packyData.value.templates[template.id] == true) return // Don't do anything if already enabled
            _packyData.update {
                it.copy(
                    //TODO make packyData immutable
                    templates = it.templates.plus(template.id to true).toMutableMap()
                )
            }
        }
    }

    fun disablePack(pack: String) {
        packy.templates[pack]?.let { template ->
            if (_packyData.value.templates[template.id] == false) return // Don't do anything if already disabled
            _packyData.update {
                it.copy(
                    //TODO immutable, as above
                    templates = it.templates.plus(template.id to false).toMutableMap()
                )
            }
        }
    }

    private fun disableConflictingPacks(template: PackyTemplate) {
        _packyData.update {
            val conflictingDisabled = it.templates.keys
                .mapNotNull(packy.templates::get)
                .filter(template::conflictsWith)
                .associate { it.id to false }

            it.copy(
                templates = it.templates.plus(conflictingDisabled).toMutableMap()
            )
        }
    }
}
