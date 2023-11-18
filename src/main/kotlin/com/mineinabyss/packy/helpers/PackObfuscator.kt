package com.mineinabyss.packy.helpers

import com.mineinabyss.idofront.messaging.*
import com.mineinabyss.packy.config.packy
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.atlas.AtlasSource
import team.unnamed.creative.atlas.SingleAtlasSource
import team.unnamed.creative.blockstate.MultiVariant
import team.unnamed.creative.blockstate.Selector
import team.unnamed.creative.blockstate.Variant
import team.unnamed.creative.font.BitMapFontProvider
import team.unnamed.creative.font.FontProvider
import team.unnamed.creative.model.ItemOverride
import team.unnamed.creative.model.Model
import team.unnamed.creative.model.ModelTexture
import team.unnamed.creative.model.ModelTextures
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import team.unnamed.creative.texture.Texture
import java.util.*

object PackObfuscator {

    private lateinit var resourcePack: ResourcePack

    private class ObfuscatedModel(val originalModel: Model, val obfuscatedModel: Model)
    private class ObfuscatedTexture(
        val originalTexture: Texture,
        val obfuscatedTexture: Texture,
        val isItemTexture: Boolean = false
    )

    private val obfuscatedModels = mutableSetOf<ObfuscatedModel>()
    private val obfuscatedTextures = mutableSetOf<ObfuscatedTexture>()

    fun obfuscatePack(resourcePack: ResourcePack) {
        logWarn("Obfuscating pack...")
        this.resourcePack = resourcePack
        obfuscatedModels.clear()
        obfuscatedTextures.clear()

        obfuscateModels()
        obfuscateFonts()
        obfuscateAtlas()
        obfuscateBlockStates()
        obfuscateTextures()
        //MinecraftResourcePackWriter.minecraft().writeToZipFile(packy.plugin.dataFolder.resolve("obfuscated.zip"), resourcePack)
        logSuccess("Finished obfuscating pack!")
    }

    private fun obfuscateModels() {
        resourcePack.models().filterNotNull().forEach models@{ model ->
            val obfuscatedModel = obfuscateModel(model)
            obfuscatedModel.overrides().filterNotNull().map { override ->
                if (override.model() in VanillaPackKeys.defaultModels) return@models
                val obfuscatedOverride =
                    resourcePack.models().find { it.key() == override.model() }?.let { obfuscateModel(it) }
                        ?: override.model()
                ItemOverride.of(obfuscatedOverride.key(), override.predicate())
            }.let {
                obfuscatedModel.overrides().clear()
                obfuscatedModel.overrides().addAll(it)
            }
        }

        obfuscatedModels.map { it.originalModel.key() }.forEach(resourcePack::removeModel)
        obfuscatedModels.map { it.obfuscatedModel }.toSet().forEach(resourcePack::model)
    }

    private fun obfuscateModel(model: Model): Model {
        if (model.key() in VanillaPackKeys.defaultModels) return model
        if (model.key() in obfuscatedModels.map { it.obfuscatedModel.key() }) return model

        return model.obfuscateModelTextures().obfuscate()
    }

    private fun Model.obfuscateModelTextures(): Model {
        val layers = textures().layers().filter { it?.key() != null }.map { modelTexture ->
            obfuscateItemTexture(modelTexture)?.keyNoPng?.let { ModelTexture.ofKey(it) } ?: modelTexture
        }
        val variables = textures().variables().map { variable ->
            variable.key to (obfuscateItemTexture(variable.value)?.keyNoPng?.let { ModelTexture.ofKey(it) } ?: variable.value)
        }.toMap()

        val particle = textures().particle()?.let { p ->
            obfuscateItemTexture(p)?.keyNoPng?.let { ModelTexture.ofKey(it) } ?: p
        }

        return this.toBuilder()
            .textures(ModelTextures.builder().layers(layers).variables(variables).particle(particle).build()).build()
    }

    private fun obfuscateFonts() {
        resourcePack.fonts().mapNotNull { it }.map { font ->
            font.providers(font.providers().filterNotNull().map { provider ->
                when (provider) {
                    is BitMapFontProvider -> provider.toBuilder().file(obfuscateFontTexture(provider)?.key() ?: provider.file()).build()
                    else -> provider
                }
            })
        }.forEach(resourcePack::font)
    }

    private fun obfuscateAtlas() {
        resourcePack.atlases().filterNotNull().map { atlas ->
            atlas.toBuilder().sources(obfuscatedTextures.filter { it.isItemTexture }.map { it.obfuscatedTexture.keyNoPng }.map(AtlasSource::single)).build()
        }.forEach(resourcePack::atlas)
    }

    private fun obfuscateTextures() {
        logWarn("Obfuscating textures...")
        resourcePack.textures().filterNotNull().forEach { texture ->
            //if (texture.keyNoPng in defaultItemKeys || texture.keyNoPng in defaultBlockKeys) return@forEach
            //obfuscatedTextures += ObfuscatedTexture(texture, texture.obfuscate())
        }

        obfuscatedTextures.forEach {
            resourcePack.removeTexture(it.originalTexture.key())
            resourcePack.texture(it.obfuscatedTexture)
        }

        logSuccess("Obfuscated textures!")
    }

    private fun obfuscateBlockStates() {
        logWarn("Obfuscating block states...")
        resourcePack.blockStates().filterNotNull().forEach { blockState ->
            val multiparts = blockState.multipart().map {
                Selector.of(it.condition(), MultiVariant.of(it.variant().variants().map { v -> v.obfuscateVariant() }))
            }
            blockState.multipart().clear()
            blockState.multipart().addAll(multiparts)

            val variants = blockState.variants().map {
                it.key to MultiVariant.of(it.value.variants().map { v -> v.obfuscateVariant() })
            }
            blockState.variants().clear()
            blockState.variants().putAll(variants)
            resourcePack.blockState(blockState)
        }
        logSuccess("Obfuscated block states!")
    }

    private fun Variant.obfuscateVariant(): Variant {
        return Variant.builder()
            .model(obfuscatedModels.find { it.originalModel.key() == model() }?.obfuscatedModel?.key() ?: model())
            .uvLock(uvLock()).weight(weight()).x(x()).y(y()).build()
    }

    private val Texture.keyNoPng get() = key().removeSuffix(".png")
    private val ModelTexture.keyNoPng get() = key()?.removeSuffix(".png")
    private val BitMapFontProvider.fileNoPng get() = file().removeSuffix(".png")
    private val SingleAtlasSource.resourceNoPng get() = resource().removeSuffix(".png")
    private fun Key.removeSuffix(suffix: String) = Key.key(asString().removeSuffix(suffix))

    private fun Model.obfuscate() = this.toBuilder().key(Key.key(UUID.randomUUID().toString())).build()
        .apply { obfuscatedModels += ObfuscatedModel(this@obfuscate, this) }

    private fun Texture.obfuscate(isItemTexture: Boolean) =
        this.toBuilder().key(Key.key(UUID.randomUUID().toString() + ".png")).build()
            .apply { obfuscatedTextures += ObfuscatedTexture(this@obfuscate, this, isItemTexture) }

    private fun obfuscateItemTexture(modelTexture: ModelTexture): Texture? {
        val keyPng = modelTexture.key()?.let { Key.key(it.removeSuffix(".png").asString() + ".png") }
        return obfuscatedTextures.find { it.originalTexture.key() == keyPng }?.obfuscatedTexture
            ?: resourcePack.textures().find { it.key() == keyPng }?.obfuscate(true)
    }

    private fun obfuscateFontTexture(provider: BitMapFontProvider): Texture? {
        val keyPng = Key.key(provider.file().removeSuffix(".png").asString() + ".png")
        return obfuscatedTextures.find { it.originalTexture.key() == keyPng }?.obfuscatedTexture
            ?: resourcePack.textures().find { it.key() == keyPng }?.obfuscate(false)
    }
}
