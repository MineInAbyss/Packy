package com.mineinabyss.packy.helpers

import com.mineinabyss.idofront.messaging.*
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackObfuscator.obfuscate
import com.mineinabyss.packy.helpers.PackObfuscator.obfuscateOverrides
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
        obfuscateTextures()
        MinecraftResourcePackWriter.minecraft().writeToZipFile(packy.plugin.dataFolder.resolve("obfuscated.zip"), resourcePack)
        logSuccess("Finished obfuscating pack!")
    }

    private fun obfuscateModels() {
        resourcePack.models().filterNotNull().map { it }.forEach models@{ model ->
            obfuscateModel(model)
        }

        obfuscatedModels.map { it.originalModel.key() }.forEach(resourcePack::removeModel)
        obfuscatedModels.map { it.obfuscatedModel }.toSet().forEach(resourcePack::model)
        obfuscateBlockStates()
    }

    private fun obfuscateModel(model: Model): Model {
        return when {
            model.key() in VanillaPackKeys.defaultModels -> model.obfuscateOverrides()
            model.key() in obfuscatedModels.map { it.obfuscatedModel.key() } -> model.obfuscateOverrides()
            else -> model.obfuscateModelTextures().obfuscate()
        }
    }

    private fun Model.obfuscateModelTextures(): Model {
        val layers = textures().layers().filter { it?.key() != null }.map { modelTexture ->
            obfuscateItemTexture(modelTexture)?.keyNoPng?.let { ModelTexture.ofKey(it) } ?: modelTexture
        }
        val variables = textures().variables().map { variable ->
            variable.key to (obfuscateItemTexture(variable.value)?.keyNoPng?.let { ModelTexture.ofKey(it) } ?: variable.value)
        }.toMap()

        val particle = textures().particle()?.let { p -> obfuscateItemTexture(p)?.keyNoPng?.let { ModelTexture.ofKey(it) } ?: p }

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

    private fun obfuscateTextures() {
        obfuscatedTextures.forEach {
            resourcePack.removeTexture(it.originalTexture.key())
            resourcePack.texture(it.obfuscatedTexture)
        }

        // Obfuscate the atlas sources
        resourcePack.atlases().filterNotNull().map { atlas ->
            atlas.toBuilder().sources(obfuscatedTextures.filter { it.isItemTexture }.map { it.obfuscatedTexture.keyNoPng }.map(AtlasSource::single)).build()
        }.forEach(resourcePack::atlas)
    }

    private fun obfuscateBlockStates() {
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
    }

    private fun Variant.obfuscateVariant(): Variant {
        return Variant.builder()
            .model(obfuscatedModels.find { it.originalModel.key() == model() }?.obfuscatedModel?.key() ?: model())
            .uvLock(uvLock()).weight(weight()).x(x()).y(y()).build()
    }

    private val Texture.keyNoPng get() = key().removeSuffix(".png")
    private fun Key.removeSuffix(suffix: String) = Key.key(asString().removeSuffix(suffix))

    private fun Model.obfuscate() = this.obfuscateOverrides().toBuilder().key(Key.key(UUID.randomUUID().toString())).build()
        .apply { obfuscatedModels += ObfuscatedModel(this@obfuscate, this) }
    private fun Model.obfuscateOverrides() = toBuilder().overrides(overrides().filterNotNull().map { override ->
        if (override.model() in VanillaPackKeys.defaultModels) return@map override
        ItemOverride.of((resourcePack.models().find { it.key() == override.model() }?.let { obfuscateModel(it) }
            ?: obfuscatedModels.find { it.originalModel.key() == override.model() || it.obfuscatedModel.key() == override.model() }?.obfuscatedModel
            ?: override.model()).key(), override.predicate())
    }).build().apply { obfuscatedModels += ObfuscatedModel(this@obfuscateOverrides, this) }

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
