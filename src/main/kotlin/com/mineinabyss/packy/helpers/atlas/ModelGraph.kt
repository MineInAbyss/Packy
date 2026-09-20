package com.mineinabyss.packy.helpers.atlas

import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.model.Model
import team.unnamed.creative.model.ModelTexture

/** Model lookups across the pack, its overlays and vanilla, cached since the same roots are walked repeatedly */
internal class ModelGraph(private val resourcePack: ResourcePack) {

    private val modelCache = mutableMapOf<Key, Model?>()
    private val textureCache = mutableMapOf<Key, Collection<Key>>()

    private fun model(key: Key): Model? = modelCache.getOrPut(key) {
        resourcePack.model(key)
            ?: resourcePack.overlays().firstNotNullOfOrNull { it.model(key) }
            ?: ResourcePacks.vanillaResourcePack.model(key)
    }

    /** The key itself plus every parent it inherits from */
    fun chainKeys(key: Key): List<Key> = chain(key).map(Model::key)

    private fun chain(key: Key): List<Model> {
        val chain = mutableListOf<Model>()
        val seen = mutableSetOf<Key>()
        var current = model(key)
        while (current != null && seen.add(current.key())) {
            chain += current
            current = current.parent()?.let(::model)
        }
        return chain
    }

    /** The models textures with parent-inheritance applied (child wins) and #references resolved */
    fun effectiveTextures(key: Key): Collection<Key> = textureCache.getOrPut(key) {
        val merged = linkedMapOf<String, ModelTexture>()
        chain(key).forEach { model ->
            model.textures().layers().forEachIndexed { index, texture -> merged.putIfAbsent("layer$index", texture) }
            model.textures().variables().forEach { (name, texture) -> merged.putIfAbsent(name, texture) }
            model.textures().particle()?.let { merged.putIfAbsent("particle", it) }
        }

        merged.values.mapNotNull { texture ->
            var resolved = texture
            var guard = 0
            while (guard++ < 16) {
                val reference = resolved.reference() ?: break
                resolved = merged[reference] ?: return@mapNotNull null
            }
            resolved.key()
        }
    }
}
