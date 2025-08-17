package com.mineinabyss.packy

import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.idofront.util.associateFast
import com.mineinabyss.idofront.util.associateFastNotNull
import com.mineinabyss.idofront.util.associateFastWith
import com.mineinabyss.idofront.util.filterFast
import com.mineinabyss.idofront.util.mapNotNullFast
import com.mineinabyss.idofront.util.toFastMap
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.packy
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.atlas.AtlasSource
import team.unnamed.creative.atlas.SingleAtlasSource
import team.unnamed.creative.blockstate.BlockState
import team.unnamed.creative.blockstate.MultiVariant
import team.unnamed.creative.blockstate.Selector
import team.unnamed.creative.blockstate.Variant
import team.unnamed.creative.font.BitMapFontProvider
import team.unnamed.creative.item.CompositeItemModel
import team.unnamed.creative.item.ConditionItemModel
import team.unnamed.creative.item.Item
import team.unnamed.creative.item.ItemModel
import team.unnamed.creative.item.RangeDispatchItemModel
import team.unnamed.creative.item.ReferenceItemModel
import team.unnamed.creative.item.SelectItemModel
import team.unnamed.creative.item.SpecialItemModel
import team.unnamed.creative.model.Model
import team.unnamed.creative.model.ModelTexture
import team.unnamed.creative.model.ModelTextures
import team.unnamed.creative.overlay.Overlay
import team.unnamed.creative.overlay.ResourceContainer
import team.unnamed.creative.sound.Sound
import team.unnamed.creative.sound.SoundRegistry
import team.unnamed.creative.texture.Texture
import java.util.*

class PackObfuscator(private val resourcePack: ResourceContainer) {

    private val isOverlay = resourcePack is Overlay

    private class ObfuscatedModel(val originalModel: Model, var obfuscatedModel: Model) {
        fun find(key: Key) = originalModel.takeIf { it.key() == key } ?: obfuscatedModel.takeIf { it.key() == key }
    }

    private class ObfuscatedTexture(
        var originalTexture: Texture,
        var obfuscatedTexture: Texture
    ) {
        fun find(key: Key) = originalTexture.takeIf { it.key() == key } ?: obfuscatedTexture.takeIf { it.key() == key }
    }

    private class ObfuscatedSound(val originalSound: Sound, val obfuscatedSound: Sound) {
        fun find(key: Key) = originalSound.takeIf { it.key() == key } ?: obfuscatedSound.takeIf { it.key() == key }
    }

    val skippedKeys = ObjectOpenHashSet<Key>()
    private val obfuscatedModels = ObjectOpenHashSet<ObfuscatedModel>()
    private val obfuscatedTextures = ObjectOpenHashSet<ObfuscatedTexture>()
    private val obfuscatedSounds = ObjectOpenHashSet<ObfuscatedSound>()

    private fun ObjectOpenHashSet<ObfuscatedModel>.findObfOrNull(key: Key) = find { it.find(key) != null }?.obfuscatedModel
    private fun ObjectOpenHashSet<ObfuscatedModel>.findObf(key: Key) = findObfOrNull(key)?.key() ?: key
    private fun ObjectOpenHashSet<ObfuscatedTexture>.findObfOrNull(key: Key) : Texture? {
        val key = key.appendSuffix(".png")
        return firstOrNull { it.find(key) != null }?.obfuscatedTexture
    }
    private fun ObjectOpenHashSet<ObfuscatedTexture>.findObf(key: Key) = findObfOrNull(key) ?: key
    private fun ObjectOpenHashSet<ObfuscatedSound>.findObf(key: Key) = find { it.find(key) != null }?.obfuscatedSound

    fun obfuscatePack() {
        if (resourcePack !is ResourcePack || packy.config.obfuscation == PackyConfig.ObfuscationType.NONE) return
        packy.logger.i("Obfuscating pack...")

        obfuscateModels()
        obfuscateFonts()
        obfuscateTextures()
        obfuscateSounds()

        packy.logger.s("Finished obfuscating pack!")
    }

    private fun obfuscateOverlays() {
        val resourcePack = (resourcePack as? ResourcePack) ?: return
        resourcePack.overlays().forEach { overlay ->
            // Create a copy of the current obfuscator, swapping the ResourceContainer
            val overlayObfuscator = PackObfuscator(overlay)

            overlayObfuscator.skippedKeys += skippedKeys
            overlayObfuscator.obfuscatedModels += obfuscatedModels
            overlayObfuscator.obfuscatedTextures += obfuscatedTextures
            overlayObfuscator.obfuscatedSounds += obfuscatedSounds
            overlayObfuscator.obfuscatedNamespaceCache += obfuscatedNamespaceCache

            overlayObfuscator.obfuscateModels()
            overlayObfuscator.obfuscateFonts()
            overlayObfuscator.obfuscateTextures()
            overlayObfuscator.obfuscateSounds()
        }
    }

    private fun obfuscateModels() {
        resourcePack.models().filterNot { it.key().value().startsWith("equipment/") }.forEach(::obfuscateModel)

        // Remove the original model and add the obfuscated one
        // If the original was marked to be skipped, still use the obfuscated but change the model-key to keep obf textures...
        obfuscatedModels.toList().forEach {
            if (resourcePack.removeModel(it.originalModel.key()) || !isOverlay) it.obfuscatedModel.addTo(resourcePack)
        }

        obfuscateBlockStates()
        obfuscateItems()
    }

    private fun obfuscateItems() {
        fun obfuscateItemModel(itemModel: ItemModel): ItemModel {
            return when (itemModel) {
                is SpecialItemModel -> ItemModel.special(itemModel.render(), obfuscatedModels.findObf(itemModel.base()))
                is ReferenceItemModel -> ItemModel.reference(obfuscatedModels.findObf(itemModel.model()), itemModel.tints())
                is CompositeItemModel -> ItemModel.composite(itemModel.models().map(::obfuscateItemModel))
                is ConditionItemModel -> ItemModel.conditional(itemModel.condition(), obfuscateItemModel(itemModel.onTrue()), obfuscateItemModel(itemModel.onFalse()))

                is SelectItemModel -> ItemModel.select(
                    itemModel.property(),
                    itemModel.cases().asSequence().map { SelectItemModel.Case._case(obfuscateItemModel(it.model()), it.`when`()) }.toList(),
                    itemModel.fallback()?.let(::obfuscateItemModel)
                )
                is RangeDispatchItemModel -> ItemModel.rangeDispatch(
                    itemModel.property(),
                    itemModel.scale(),
                    itemModel.entries().asSequence().map { RangeDispatchItemModel.Entry.entry(it.threshold(), obfuscateItemModel(it.model())) }.toList(),
                    itemModel.fallback()?.let(::obfuscateItemModel)
                )
                else -> itemModel
            }
        }

        resourcePack.items().toList().forEach { item ->
            Item.item(item.key(), obfuscateItemModel(item.model()), item.handAnimationOnSwap()).addTo(resourcePack)
        }
    }

    private fun obfuscateBlockStates() {
        resourcePack.blockStates().toList().forEach { blockState ->
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
        resourcePack.fonts().toList().forEach { font ->
            font.providers(font.providers().map { provider ->
                when (provider) {
                    is BitMapFontProvider -> provider.toBuilder().file(obfuscateFontTexture(provider)?.key() ?: provider.file()).build()
                    else -> provider
                }
            }).addTo(resourcePack)
        }
    }

    private fun obfuscateTextures() {
        obfuscatedTextures.forEach {
            val originalKey = it.originalTexture.key()

            // Add the obfuscated texture if the original exists or if this is not an overlay
            if (resourcePack.removeTexture(originalKey) || !isOverlay) {
                resourcePack.texture(it.obfuscatedTexture)
            }

            // Handle emissive textures similarly
            resourcePack.texture(originalKey.emissiveKey())?.also { emissive ->
                resourcePack.removeTexture(emissive.key())
                resourcePack.texture(emissive.toBuilder().key(it.obfuscatedTexture.key().emissiveKey()).build())
            }
        }

        obfuscateAtlases()
    }

    private fun obfuscateAtlases() {
        resourcePack.atlases().toList().forEach { atlas ->
            val obfSources = atlas.sources().map { source ->
                val obfSource = (source as? SingleAtlasSource)?.resource()?.let { obfuscatedTextures.findObfOrNull(it) } ?: return@map source
                AtlasSource.single(obfSource.key().removeSuffix(".png"))
            }

            atlas.toBuilder().sources(obfSources).build().addTo(resourcePack)
        }
    }

    private fun obfuscateSounds() {
        resourcePack.sounds().toList().forEach { sound ->
            //if (sound.key() in vanillaSounds) return@forEach
            Sound.sound(sound.key().obfuscateKey(), sound.data()).also {
                obfuscatedSounds += ObfuscatedSound(sound, it)
            }.addTo(resourcePack)
        }

        resourcePack.soundRegistries().toList().forEach soundRegistries@{ soundRegistry ->
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

        // Remove the original sounds and add the obfuscated sounds
        obfuscatedSounds.forEach { obfSound ->
            if (resourcePack.removeSound(obfSound.originalSound.key()) || !isOverlay) {
                obfSound.obfuscatedSound.addTo(resourcePack)
            }
        }
    }


    private fun obfuscateModel(model: Model): Model {
        obfuscatedModels.findObfOrNull(model.key())?.let { return it }
        val builder = model.toBuilder()

        obfuscateModelTextures(model, builder)
        (model.key().takeUnless { model.key() in skippedKeys || ResourcePacks.vanillaResourcePack.model(it) != null }?.obfuscateKey())?.apply(builder::key)

        val obfuscatedModel = ObfuscatedModel(model, builder.build()).apply(obfuscatedModels::add)
        obfuscateParentModel(model, builder)
        obfuscatedModel.obfuscatedModel = builder.build()

        return obfuscatedModel.obfuscatedModel
    }

    private fun obfuscateModelTextures(model: Model, builder: Model.Builder) {
        obfuscatedModels.findObfOrNull(model.key())?.let { builder.textures(it.textures()); return }
        if (ResourcePacks.vanillaResourcePack.model(model.key()) != null) return

        val layers = model.textures().layers().mapNotNullFast { modelTexture ->
            if (modelTexture.key() == null) return@mapNotNullFast null
            obfuscateModelTexture(modelTexture)?.key()?.removeSuffix(".png")?.let(ModelTexture::ofKey) ?: modelTexture
        }
        val variables = model.textures().variables().mapValues { variable ->
            obfuscateModelTexture(variable.value)?.key()?.removeSuffix(".png")?.let(ModelTexture::ofKey) ?: variable.value
        }

        val particle = model.textures().particle()?.let { p -> obfuscateModelTexture(p)?.key()?.removeSuffix(".png")?.let(ModelTexture::ofKey) ?: p }
        val modelTextures = ModelTextures.builder().layers(layers).variables(variables).particle(particle).build()

        builder.textures(modelTextures)
    }

    private fun Variant.obfuscateVariant(): Variant {
        return Variant.builder()
            .model(obfuscatedModels.findObf(model()).key())
            .uvLock(uvLock()).weight(weight()).x(x()).y(y()).build()
    }

    private fun obfuscateParentModel(model: Model, builder: Model.Builder) {
        val obfuscatedParent = when(val parent = model.parent()) {
            null -> return
            else -> obfuscatedModels.findObfOrNull(parent)?.key()
                ?: ResourcePacks.vanillaResourcePack.model(parent)?.let { parent }
                ?: resourcePack.takeUnless { parent == model.key() }?.model(parent)?.let(::obfuscateModel)?.key()
                ?: parent
        }

        builder.parent(obfuscatedParent)
    }


    private fun Key.removeSuffix(suffix: String) = Key.key(asString().removeSuffix(suffix))
    private fun Key.appendSuffix(suffix: String) = Key.key(asString().removeSuffix(suffix).plus(suffix))

    private fun Texture.obfuscate() =
        this.toBuilder().key(this.key().obfuscateKey().appendSuffix(".png")).build()
            .also { obfuscatedTextures += ObfuscatedTexture(this@obfuscate, it) }

    private fun obfuscateModelTexture(modelTexture: ModelTexture): Texture? {
        val key = modelTexture.key()?.appendSuffix(".png") ?: return null
        return obfuscatedTextures.findObfOrNull(key) ?: vanillaModelTextures[key] ?: resourcePack.texture(key)?.obfuscate()
    }

    private fun obfuscateFontTexture(provider: BitMapFontProvider): Texture? {
        val key = provider.file().appendSuffix(".png")
        return obfuscatedTextures.findObfOrNull(key) ?: vanillaFontTextures[key] ?: resourcePack.texture(key)?.obfuscate()
    }

    private val obfuscatedNamespaceCache = mutableMapOf<String, String>()
    private fun Key.obfuscateKey() = when (packy.config.obfuscation) {
        PackyConfig.ObfuscationType.NONE -> this
        PackyConfig.ObfuscationType.FULL -> Key.key(obfuscatedNamespaceCache.getOrPut(namespace()) {
            UUID.randomUUID().toString()
        }, UUID.randomUUID().toString())
        PackyConfig.ObfuscationType.SIMPLE -> Key.key(this.namespace(), UUID.randomUUID().toString())
    }

    private fun Key.emissiveKey() = removeSuffix(".png").appendSuffix("_e.png")

    private val vanillaModelTextures by lazy {
        resourcePack.models().filterFast { ResourcePacks.vanillaResourcePack.model(it.key()) != null }
            .plus(ResourcePacks.vanillaResourcePack.models())
            .distinctBy { it.key().asString() }
            .mapNotNull { it.textures().layers() + listOfNotNull(it.textures().particle()) + it.textures().variables().values }.flatten()
            .associateFastNotNull {
                val key = it.key()?.appendSuffix(".png") ?: return@associateFastNotNull null
                key to (resourcePack.texture(key) ?: ResourcePacks.vanillaResourcePack.texture(key) ?: return@associateFastNotNull null)
            }
    }

    private val vanillaFontTextures by lazy {
        resourcePack.fonts().filterFast { ResourcePacks.vanillaResourcePack.font(it.key()) != null }
            .plus(ResourcePacks.vanillaResourcePack.fonts())
            .distinctBy { it.key().asString() }
            .mapNotNull { it.providers().mapNotNullFast { (it as? BitMapFontProvider)?.file()?.appendSuffix(".png") } }.flatten()
            .associateFastWith { resourcePack.texture(it) ?: ResourcePacks.vanillaResourcePack.texture(it) ?: return@associateFastWith null }
    }
}
