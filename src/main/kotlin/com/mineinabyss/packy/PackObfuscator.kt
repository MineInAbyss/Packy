package com.mineinabyss.packy

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.idofront.util.associateFastWith
import com.mineinabyss.idofront.util.filterFast
import com.mineinabyss.idofront.util.flatMapFast
import com.mineinabyss.idofront.util.mapNotNullFast
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.JsonBuilder.array
import com.mineinabyss.packy.helpers.JsonBuilder.`object`
import com.mineinabyss.packy.helpers.JsonBuilder.plus
import com.mineinabyss.packy.helpers.ModernVersionPatcher
import com.mineinabyss.packy.helpers.toJsonObject
import com.mineinabyss.packy.helpers.toWritable
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.atlas.AtlasSource
import team.unnamed.creative.atlas.SingleAtlasSource
import team.unnamed.creative.base.Writable
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
import kotlin.text.get
import kotlin.text.removeSuffix

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

    val skippedKeys = ObjectOpenHashSet<Key>()
    private val obfuscatedModels = ObjectOpenHashSet<ObfuscatedModel>()
    private val obfuscatedTextures = ObjectOpenHashSet<ObfuscatedTexture>()
    private val obfuscatedSounds = ObjectOpenHashSet<ObfuscatedSound>()

    private fun ObjectOpenHashSet<ObfuscatedModel>.findObf(key: Key) = firstOrNull { it.find(key) != null }?.obfuscatedModel
    private fun ObjectOpenHashSet<ObfuscatedTexture>.findObf(key: Key) = firstOrNull { it.find(key) != null }?.obfuscatedTexture
    private fun ObjectOpenHashSet<ObfuscatedSound>.findObf(key: Key) = firstOrNull { it.find(key) != null }?.obfuscatedSound

    fun obfuscatePack() {
        if (packy.config.obfuscation.type == PackyConfig.Obfuscation.Type.NONE) return 
        packy.logger.i("Obfuscating pack...")

        obfuscateModels()
        obfuscateFonts()
        obfuscateTextures()
        obfuscateSounds()

        packy.logger.s("Finished obfuscating pack!")
    }

    private fun obfuscateModels() {
        resourcePack.models().filterNot { it.key().value().startsWith("equipment/") }.forEach(::obfuscateModel)

        // Remove the original model and add the obfuscated one
        // If the original was marked to be skipped, still use the obfuscated but change the model-key to keep obf textures...
        obfuscatedModels.forEach {
            resourcePack.removeModel(it.originalModel.key())
            if (it.originalModel.key() !in skippedKeys) it.obfuscatedModel.addTo(resourcePack)
            else it.obfuscatedModel.toBuilder().key(it.originalModel.key()).build().addTo(resourcePack)
        }

        obfuscateBlockStates()
        obfuscateItemModels()
    }

    private fun obfuscateItemModels() {

        fun obfuscateItemModel(obj: JsonObject) {
            val modelObj = obj.`object`("model") ?: return
            modelObj.get("model")?.asString?.let(Key::key)?.let { obfuscatedModels.findObf(it) }?.let {
                obj.`object`("model")!!.plus("model", it.key().asString())
            }
            obj.`object`("fallback")?.get("model")?.asString?.let(Key::key)?.let { obfuscatedModels.findObf(it) }?.let {
                obj.`object`("fallback")!!.plus("model", it.key().asString())
            }

            modelObj.array("entries")?.forEach { it.asJsonObject?.let(::obfuscateItemModel) }
            modelObj.array("cases")?.forEach { it.asJsonObject?.let(::obfuscateItemModel) }

            modelObj.`object`("on_false")?.let { onFalse ->
                onFalse.array("entries")?.forEach { it.asJsonObject?.let(::obfuscateItemModel) }
                onFalse.array("cases")?.forEach { it.asJsonObject?.let(::obfuscateItemModel) }
                onFalse.`object`("fallback")?.get("model")?.asString?.let(Key::key)?.let { obfuscatedModels.findObf(it) }?.let {
                    onFalse.`object`("fallback")!!.plus("model", it.key().asString())
                }
            }
            modelObj.`object`("on_true")?.let { onTrue ->
                onTrue.array("entries")?.forEach { it.asJsonObject?.let(::obfuscateItemModel) }
                onTrue.array("cases")?.forEach { it.asJsonObject?.let(::obfuscateItemModel) }
                onTrue.`object`("fallback")?.get("model")?.asString?.let(Key::key)?.let { obfuscatedModels.findObf(it) }?.let {
                    onTrue.`object`("fallback")!!.plus("model", it.key().asString())
                }
            }
        }

        resourcePack.unknownFiles().filterFast { it.key.startsWith("assets/minecraft/items/") }.forEach { (key, writable) ->
            runCatching {
                val itemModelObject = writable.toJsonObject() ?: return@forEach
                if (ModernVersionPatcher.isStandardItemModel(key, itemModelObject)) return@forEach
                obfuscateItemModel(itemModelObject)

                resourcePack.unknownFile(key, itemModelObject.toWritable())
            }.onFailure {
                it.printStackTrace()
            }
        }
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
        resourcePack.fonts().filterNotNull().map { font ->
            font.providers(font.providers().filterNotNull().map { provider ->
                when (provider) {
                    is BitMapFontProvider ->
                        provider.toBuilder().file(obfuscateFontTexture(provider)?.key() ?: provider.file()).build()
                    else -> provider
                }
            })
        }.forEach(resourcePack::font)
    }

    private fun obfuscateTextures() {
        obfuscatedTextures.forEach {
            resourcePack.removeTexture(it.originalTexture.key())
            resourcePack.texture(it.obfuscatedTexture)
            resourcePack.texture(it.originalTexture.key().emissiveKey())?.also { e ->
                resourcePack.removeTexture(e.key())
                resourcePack.texture(e.toBuilder().key(it.obfuscatedTexture.key().emissiveKey()).build())
            }
        }

        obfuscateAtlases()
    }

    private fun obfuscateAtlases() {
        resourcePack.atlases().map { atlas ->
            val obfSources = atlas.sources().filter { it !is SingleAtlasSource }.plus(
                atlas.sources().filterIsInstance<SingleAtlasSource>().map { s ->
                    obfuscatedTextures.findObf(s.resource())?.let {
                        AtlasSource.single(it.key().removeSuffix(".png"))
                    } ?: s
                }
            )

            atlas.toBuilder().sources(obfSources).build()
        }.forEach(resourcePack::atlas)
    }

    private fun obfuscateSounds() {
        resourcePack.sounds().map { sound ->
            Sound.sound(sound.key().obfuscateKey(), sound.data()).also {
                obfuscatedSounds += ObfuscatedSound(sound, it)
            }
        }.forEach(resourcePack::sound)

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


    private fun obfuscateModel(model: Model) = obfuscatedModels.findObf(model.key())
        ?: model.obfuscateModelTextures()
            .obfuscateOverrides()
            .also { obfuscatedModels += ObfuscatedModel(model, it) }
            .obfuscateParentModel()
            .also { obfuscatedModels.removeIf { it.originalModel.key() == model.key() } }
            .also { obfuscatedModels += ObfuscatedModel(model, it) }

    private fun Model.obfuscateModelTextures(): Model {
        obfuscatedModels.findObf(key())?.let { return it }
        if (ResourcePacks.defaultVanillaResourcePack.model(key()) != null) return this

        val layers = textures().layers().filter { it.key() != null }.map { modelTexture ->
            obfuscateModelTexture(modelTexture)?.key()?.removeSuffix(".png")?.let(ModelTexture::ofKey) ?: modelTexture
        }
        val variables = textures().variables().map { variable ->
            variable.key to (obfuscateModelTexture(variable.value)?.key()?.removeSuffix(".png")?.let(ModelTexture::ofKey) ?: variable.value)
        }.toMap()

        val particle = textures().particle()
            ?.let { p -> obfuscateModelTexture(p)?.key()?.removeSuffix(".png")?.let { ModelTexture.ofKey(it) } ?: p }
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
        return toBuilder().parent(
            obfuscatedModels.findObf(parent)?.key()
                ?: ResourcePacks.defaultVanillaResourcePack.model(parent)?.let { return this }
                ?: resourcePack.takeUnless { parent == key() }?.model(parent)?.let(::obfuscateModel)?.key()
                ?: parent
        ).build()
    }

    private fun Model.obfuscateOverrides(): Model = obfuscatedModels.findObf(key())
        ?: toBuilder().overrides(overrides().filterNotNull().map { override ->
            val overrideKey = override.model()
            val modelKey = obfuscatedModels.findObf(overrideKey)?.key()
                ?: ResourcePacks.defaultVanillaResourcePack.model(overrideKey)?.let { overrideKey }
                ?: resourcePack.takeUnless { overrideKey == this.key() }?.model(overrideKey)?.let(::obfuscateModel)?.key()
                ?: overrideKey

            return@map ItemOverride.of(modelKey, override.predicate())
        }
        ).key(key().takeUnless { ResourcePacks.defaultVanillaResourcePack.model(it) != null }?.obfuscateKey() ?: key())
            .build()


    private fun Key.removeSuffix(suffix: String) = Key.key(asString().removeSuffix(suffix))
    private fun Key.appendSuffix(suffix: String) = Key.key(asString().removeSuffix(suffix).plus(suffix))

    private fun Texture.obfuscate() =
        this.toBuilder().key(this.key().obfuscateKey().appendSuffix(".png")).build()
            .also { obfuscatedTextures += ObfuscatedTexture(this@obfuscate, it) }

    private fun obfuscateModelTexture(modelTexture: ModelTexture): Texture? {
        val keyPng = modelTexture.key()?.appendSuffix(".png") ?: return null
        return obfuscatedTextures.findObf(keyPng)
            ?: vanillaModelTextures[keyPng]
            ?: resourcePack.texture(keyPng)?.obfuscate()
    }

    private fun obfuscateFontTexture(provider: BitMapFontProvider): Texture? {
        val key = provider.file()
        return obfuscatedTextures.findObf(key)
            ?: ResourcePacks.defaultVanillaResourcePack.texture(key)
            ?: resourcePack.texture(key)?.obfuscate()
    }

    private val obfuscatedNamespaceCache = mutableMapOf<String, String>()
    private fun Key.obfuscateKey() = when (packy.config.obfuscation.type) {
        PackyConfig.Obfuscation.Type.NONE -> this
        PackyConfig.Obfuscation.Type.FULL -> Key.key(obfuscatedNamespaceCache.getOrPut(namespace()) {
            UUID.randomUUID().toString()
        }, UUID.randomUUID().toString())

        PackyConfig.Obfuscation.Type.SIMPLE -> Key.key(this.namespace(), UUID.randomUUID().toString())
    }

    private fun Key.emissiveKey() = removeSuffix(".png").appendSuffix("_e.png")

    private val vanillaModelTextures by lazy {
        resourcePack.models().filterFast { ResourcePacks.defaultVanillaResourcePack.model(it.key()) != null }
            .plus(ResourcePacks.defaultVanillaResourcePack.models())
            .distinctBy { it.key().asString() }
            .flatMapFast { it.textures().layers().plus(it.textures().variables().values).plus(it.textures().particle()) }
            .mapNotNullFast { it?.key()?.appendSuffix(".png") }
            .associateFastWith { resourcePack.texture(it) ?: ResourcePacks.defaultVanillaResourcePack.texture(it) }
            .filterFast { it.value != null } as Object2ObjectOpenHashMap<Key, Texture>
    }
}
