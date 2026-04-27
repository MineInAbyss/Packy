package com.mineinabyss.packy

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.mineinabyss.dependencies.*
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.papermc.gearyPaper
import com.mineinabyss.idofront.config.SingleConfig
import com.mineinabyss.idofront.features.*
import com.mineinabyss.idofront.messaging.ComponentLogger
import com.mineinabyss.packy.config.PackyAccessToken
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.PackyContext
import com.mineinabyss.packy.config.PackyMenu
import com.mineinabyss.packy.config.PackyTemplates
import kotlinx.serialization.modules.EmptySerializersModule
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import team.unnamed.creative.ResourcePack

class PackyPlugin : JavaPlugin(), DI, PackyContext {
    override val di: DIContext = DI {
        single<Plugin> { this@PackyPlugin }
        singlePluginLogger(this@PackyPlugin)
        singleConfig<PackyConfig>("config.yml") { default = PackyConfig() }
        singleConfig<PackyMenu>("menu.yml") { default = PackyMenu() }
        singleConfig<PackyAccessToken>("accessToken.yml") { default = PackyAccessToken() }
        singleConfig<PackyTemplates>("templates.yml") {
            default = PackyTemplates()
            format = Yaml(
                serializersModule = EmptySerializersModule(),
                YamlConfiguration(
                    polymorphismStyle = PolymorphismStyle.Property,
                    encodeDefaults = true,
                    strictMode = false,
                    sequenceBlockIndent = 2,
                    singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous
                )
            )
        }
        single {
            MainCommand(
                names = listOf("packy"),
                description = "Packy main command",
                reloadCommandName = "reload",
                onBeforeReload = {
                    get<SingleConfig<PackyConfig>>().updateCached()
                    get<SingleConfig<PackyMenu>>().updateCached()
                    get<SingleConfig<PackyAccessToken>>().updateCached()
                    get<SingleConfig<PackyTemplates>>().updateCached()
                }
            )
        }
    }

    override val config: PackyConfig by di.getLazy()
    override val menu: PackyMenu by di.getLazy()
    override val accessToken: PackyAccessToken by di.getLazy()
    override val templates: PackyTemplates by di.getLazy()
    override val logger: ComponentLogger by di.getLazy()
    override val defaultPack: ResourcePack = ResourcePack.resourcePack()

    override fun onLoad() {
        PackyContext.instance = this
    }

    override fun onEnable() {
        gearyPaper.configure {
            world.autoscan {
                scan(this@PackyPlugin.classLoader, listOf("com.mineinabyss.packy")) {
                    all()
                }
            }
        }
        scope.loadAllCatching(
            PackyMainFeature
        )
        scope.loadCatching(MainCommandFeature)
    }
}

