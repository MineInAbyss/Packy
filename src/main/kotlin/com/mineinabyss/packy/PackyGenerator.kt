package com.mineinabyss.packy

import com.mineinabyss.idofront.messaging.*
import com.mineinabyss.packy.components.packyData
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyServer
import com.mineinabyss.packy.helpers.PackyServer.playerPack
import com.mineinabyss.packy.helpers.PackyServer.playerPacks
import com.mineinabyss.packy.helpers.toPackMeta
import net.kyori.adventure.key.Key
import org.apache.commons.io.filefilter.FileFileFilter
import org.bukkit.entity.Player
import team.unnamed.creative.BuiltResourcePack
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackReader
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import kotlin.io.path.div
import kotlin.io.path.pathString

object PackyGenerator {

    fun setupForcedPackFiles() {
        packy.config.zipDestination.toFile().deleteRecursively()
        packy.defaultPack.icon(Writable.path(packy.plugin.dataFolder.toPath() / packy.config.icon))
        packy.defaultPack.packMeta(packy.config.mcmeta.format, packy.config.mcmeta.description)

        // Add all forced packs to defaultPack
        packy.templates.filter { it.forced }.forEach { template ->
            val templatePath = packy.plugin.dataFolder.toPath() / "templates" / template.id
            val templatePack = MinecraftResourcePackReader.minecraft().readFromDirectory(templatePath.toFile())
            mergePacks(packy.defaultPack, templatePack)
            logSuccess("Added ${template.id}-template to defaultPack")
        }
    }

    fun createPlayerPack(player: Player): ResourcePack {
        val pack = mergePacks(ResourcePack.create(), packy.defaultPack)

        // Filters out all forced files as they are already in defaultPack
        // Filter all TemplatePacks that are not default or not in players enabledPackAddons
        packy.templates.filter { !it.forced && it in player.packyData.enabledPackAddons }.forEach { template ->
            val templatePath = packy.plugin.dataFolder.toPath() / "templates" / template.id
            val templatePack = MinecraftResourcePackReader.minecraft().readFromDirectory(templatePath.toFile())
            mergePacks(pack, templatePack)
            logSuccess("Added ${template.id}-template to pack")
        }

        val playerPacks = (packy.plugin.dataFolder.toPath() / "playerPacks" / player.uniqueId.toString()).toFile().apply { deleteRecursively() }
        MinecraftResourcePackWriter.minecraft().writeToDirectory(playerPacks, pack)
        player.playerPack = pack
        return pack
    }

    private fun mergePacks(basePack: ResourcePack, mergePack: ResourcePack): ResourcePack {
        mergePack.textures().forEach(basePack::texture)
        mergePack.sounds().forEach(basePack::sound)
        mergePack.unknownFiles().forEach(basePack::unknownFile)
        mergePack.packMeta()?.let { basePack.packMeta(it.format(), it.description().ifEmpty { basePack.description() }) }
        mergePack.icon()?.let { basePack.icon(it) }

        mergePack.models().forEach { model ->
            val baseModel = basePack.model(model.key()) ?: return@forEach basePack.model(model)
            basePack.model(model.apply { overrides().addAll(baseModel.overrides()) })
        }
        mergePack.fonts().forEach { font ->
            val baseFont = basePack.font(font.key()) ?: return@forEach basePack.font(font)
            basePack.font(font.key(), baseFont.providers().apply { addAll(font.providers()) })
        }
        mergePack.soundRegistries().forEach { soundRegistry ->
            val baseRegistry = basePack.soundRegistry(soundRegistry.namespace()) ?: return@forEach basePack.soundRegistry(soundRegistry)
            basePack.soundRegistry(baseRegistry.apply { sounds().addAll(soundRegistry.sounds()) })
        }
        mergePack.atlases().forEach { atlas ->
            val baseAtlas = basePack.atlas(atlas.key()) ?: return@forEach basePack.atlas(atlas)
            basePack.atlas(baseAtlas.toBuilder().apply { sources().addAll(atlas.sources()) }.build())
        }
        mergePack.languages().forEach { language ->
            val baseLanguage = basePack.language(language.key()) ?: return@forEach basePack.language(language)
            basePack.language(baseLanguage.apply { translations().putAll(language.translations()) })
        }
        mergePack.blockStates().forEach { blockState ->
            val baseBlockState = basePack.blockState(blockState.key()) ?: return@forEach basePack.blockState(blockState)
            basePack.blockState(baseBlockState.apply { variants().putAll(blockState.variants()) })
        }

        return basePack
    }
}
