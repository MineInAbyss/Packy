package com.mineinabyss.packy

import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.packy
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.atlas.AtlasSource
import team.unnamed.creative.atlas.SingleAtlasSource
import team.unnamed.creative.blockstate.BlockState
import team.unnamed.creative.blockstate.MultiVariant
import team.unnamed.creative.blockstate.Selector
import team.unnamed.creative.blockstate.Variant
import team.unnamed.creative.font.BitMapFontProvider
import team.unnamed.creative.model.ItemOverride
import team.unnamed.creative.model.Model
import team.unnamed.creative.model.ModelTexture
import team.unnamed.creative.model.ModelTextures
import team.unnamed.creative.sound.Sound
import team.unnamed.creative.sound.SoundRegistry
import team.unnamed.creative.texture.Texture
import java.util.*

class PackObfuscator(private val resourcePack: ResourcePack) {

    private class ObfuscatedModel(val originalModel: Model, val obfuscatedModel: Model) {
        fun find(key: Key) = originalModel.takeIf { it.key() == key } ?: obfuscatedModel.takeIf { it.key() == key }
    }

    private class ObfuscatedTexture(
        val originalTexture: Texture,
        val obfuscatedTexture: Texture
    ) {
        fun find(key: Key) = originalTexture.takeIf { it.key() == key } ?: obfuscatedTexture.takeIf { it.key() == key }
    }

    private class ObfuscatedSound(val originalSound: Sound, val obfuscatedSound: Sound) {
        fun find(key: Key) = originalSound.takeIf { it.key() == key } ?: obfuscatedSound.takeIf { it.key() == key }
    }

    private val obfuscatedModels = mutableSetOf<ObfuscatedModel>()
    private val obfuscatedTextures = mutableSetOf<ObfuscatedTexture>()
    private val obfuscatedSounds = mutableSetOf<ObfuscatedSound>()

    private fun Set<ObfuscatedModel>.findObf(key: Key) = firstOrNull { it.find(key) != null }?.obfuscatedModel
    private fun Set<ObfuscatedTexture>.findObf(key: Key) = firstOrNull { it.find(key) != null }?.obfuscatedTexture
    private fun Set<ObfuscatedSound>.findObf(key: Key) = firstOrNull { it.find(key) != null }?.obfuscatedSound

    fun obfuscatePack() {
        if (packy.config.obfuscation == PackyConfig.ObfuscationType.NONE) return
        packy.logger.i("Obfuscating pack...")

        obfuscatedModels.clear()
        obfuscatedTextures.clear()

        obfuscateModels()
        obfuscateFonts()
        obfuscateTextures()
        obfuscateSounds()

        packy.logger.s("Finished obfuscating pack!")
    }

    private fun obfuscateModels() {
        resourcePack.models().filterNotNull().forEach(::obfuscateModel)

        obfuscatedModels.forEach {
            resourcePack.removeModel(it.originalModel.key())
            it.obfuscatedModel.addTo(resourcePack)
        }

        obfuscateBlockStates()
    }

    private fun obfuscateBlockStates() {
        resourcePack.blockStates().filterNotNull().forEach { blockState ->
            val multiparts = blockState.multipart().map {
                Selector.of(it.condition(), MultiVariant.of(it.variant().variants().map { v -> v.obfuscateVariant() }))
            }

            val variants = blockState.variants().map {
                it.key to MultiVariant.of(it.value.variants().map { v -> v.obfuscateVariant() })
            }.toMap()

            BlockState.of(blockState.key(), variants, multiparts).addTo(resourcePack)
        }
    }

    private fun obfuscateFonts() {
        resourcePack.fonts().mapNotNull { it }.map { font ->
            font.providers(font.providers().filterNotNull().map { provider ->
                when (provider) {
                    is BitMapFontProvider -> provider.toBuilder()
                        .file(obfuscateFontTexture(provider)?.key() ?: provider.file()).build()

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

        obfuscateAtlases()
    }

    private fun obfuscateAtlases() {
        resourcePack.atlases().map { atlas ->
            val nonSingleSources = atlas.sources().filterNot { it is SingleAtlasSource }.toMutableList()
            val obfSources = atlas.sources().filterIsInstance<SingleAtlasSource>().map { s ->
                obfuscatedTextures.firstOrNull { it.find(s.resource()) != null }?.let {
                    AtlasSource.single(it.obfuscatedTexture.key().removeSuffix(".png"))
                } ?: s
            }

            nonSingleSources += obfSources
            atlas.toBuilder().sources(nonSingleSources).build()
        }.forEach(resourcePack::atlas)
    }

    private fun obfuscateSounds() {
        resourcePack.sounds().map { sound ->
            val obfSound = Sound.sound(sound.key().obfuscateKey(), sound.data())
            obfuscatedSounds += ObfuscatedSound(sound, obfSound)
            return@map obfSound
        }.toList().forEach(resourcePack::sound)

        resourcePack.soundRegistries().map soundRegistries@{ soundRegistry ->
            SoundRegistry.soundRegistry(
                soundRegistry.namespace(),
                soundRegistry.sounds().map soundEvents@{ soundEvent ->
                    soundEvent.toBuilder().sounds(soundEvent.sounds().map soundEntries@{ soundEntry ->
                        obfuscatedSounds.findObf(soundEntry.key())?.let {
                            soundEntry.toBuilder().key(it.key()).build()
                        } ?: soundEntry
                    }).build()
                }).addTo(resourcePack)
        }

        obfuscatedSounds.forEach {
            resourcePack.removeSound(it.originalSound.key())
            it.obfuscatedSound.addTo(resourcePack)
        }
    }


    private fun obfuscateModel(model: Model) =
        obfuscatedModels.findObf(model.key()) ?: model.obfuscateModelTextures().obfuscateParentModel().obfuscateOverrides()

    private fun Model.obfuscateModelTextures(): Model {
        obfuscatedModels.findObf(this.key())?.let { return it }

        val layers = textures().layers().filter { it.key() != null }.map { modelTexture ->
            obfuscateModelTexture(modelTexture)?.key()?.let(ModelTexture::ofKey) ?: modelTexture
        }
        val variables = textures().variables().map { variable ->
            variable.key to (obfuscateModelTexture(variable.value)?.key()?.let(ModelTexture::ofKey) ?: variable.value)
        }.toMap()

        val particle = textures().particle()
            ?.let { p -> obfuscateModelTexture(p)?.key()?.let { ModelTexture.ofKey(it) } ?: p }
        val modelTextures = ModelTextures.builder().layers(layers).variables(variables).particle(particle).build()
        return this.toBuilder().textures(modelTextures).build()
    }

    private fun Variant.obfuscateVariant(): Variant {
        return Variant.builder()
            .model(obfuscatedModels.findObf(model())?.key() ?: model())
            .uvLock(uvLock()).weight(weight()).x(x()).y(y()).build()
    }

    private fun Model.obfuscateParentModel(): Model {
        val parent = parent() ?: return this
        obfuscatedModels.findObf(key())?.let { return it }

        return toBuilder().parent(
            obfuscatedModels.findObf(parent)?.key()
            ?: resourcePack.takeUnless { parent == key() }?.model(parent)?.let(::obfuscateModel)?.key()
            ?: parent
        ).build()
    }

    private fun Model.obfuscateOverrides(): Model = obfuscatedModels.findObf(key()) ?: toBuilder().overrides(
        overrides().filterNotNull().map { override ->
            val overrideKey = override.model()
            if (ResourcePacks.defaultVanillaResourcePack?.model(overrideKey) != null) return@map override
            val modelKey = obfuscatedModels.findObf(overrideKey)?.key()
                ?: resourcePack.takeUnless { overrideKey == this.key() }?.model(overrideKey)?.let(::obfuscateModel)?.key()
                ?: overrideKey

            return@map ItemOverride.of(modelKey, override.predicate())
        }
    ).key(key().takeUnless { ResourcePacks.defaultVanillaResourcePack?.model(it) != null }?.obfuscateKey() ?: key()).build()
        .also { obfuscatedModels += ObfuscatedModel(this@obfuscateOverrides, it) }


    private fun Key.removeSuffix(suffix: String) = Key.key(asString().removeSuffix(suffix))
    private fun Key.appendSuffix(suffix: String) = Key.key(asString().removeSuffix(suffix).plus(suffix))

    private fun Texture.obfuscate() =
        this.toBuilder().key(this.key().obfuscateKey()).build()
            .also { obfuscatedTextures += ObfuscatedTexture(this@obfuscate, it) }

    private fun obfuscateModelTexture(modelTexture: ModelTexture): Texture? {
        val keyPng = modelTexture.key()?.removeSuffix(".png") ?: return null
        return obfuscatedTextures.findObf(keyPng) ?: resourcePack.texture(keyPng)?.obfuscate()
    }

    private fun obfuscateFontTexture(provider: BitMapFontProvider): Texture? {
        val keyPng = provider.file().appendSuffix(".png")
        return obfuscatedTextures.findObf(keyPng) ?: resourcePack.texture(keyPng)?.obfuscate()
    }

    private fun Key.obfuscateKey() = when (packy.config.obfuscation) {
        PackyConfig.ObfuscationType.NONE -> this
        PackyConfig.ObfuscationType.FULL -> Key.key(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        PackyConfig.ObfuscationType.SIMPLE -> Key.key(this.namespace(), UUID.randomUUID().toString())
    }.let { if (asString().endsWith(".png")) it.appendSuffix(".png") else it.removeSuffix(".png") }
}
