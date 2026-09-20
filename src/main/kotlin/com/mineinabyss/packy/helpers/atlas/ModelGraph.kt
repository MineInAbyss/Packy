package com.mineinabyss.packy.helpers.atlas

import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.model.Model
import team.unnamed.creative.model.ModelTexture
import team.unnamed.creative.model.ModelTextures

/** Model lookups across the pack, its overlays and vanilla, cached since the same roots are walked repeatedly */
internal class ModelGraph(private val resourcePack: ResourcePack) {

    private val modelCache = mutableMapOf<Key, Model?>()
    private val textureCache = mutableMapOf<Key, Collection<Key>>()

    fun model(key: Key): Model? = modelCache.getOrPut(key) {
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
        resolved(key).values.mapNotNull(ModelTexture::key)
    }

    /**
     * Inheritance and `#references` collapsed onto the model itself, so a clone carries its textures
     * without its parent, which the other atlas side may rewrite out from under it.
     */
    fun flattened(key: Key, remap: Map<Key, Key>): ModelTextures {
        val variables = resolved(key).mapValues { (_, texture) ->
            texture.key()?.let { remap[it] }?.let(ModelTexture::ofKey) ?: texture
        }
        val layers = variables.keys.filter { it.startsWith("layer") }
            .sortedBy { it.removePrefix("layer").toIntOrNull() ?: 0 }
            .map { variables.getValue(it) }

        return ModelTextures.builder()
            .layers(layers)
            .particle(variables["particle"])
            .variables(variables.filterKeys { !it.startsWith("layer") && it != "particle" })
            .build()
    }

    /** Named slot to the texture it ends up at, walking parents and `#reference` indirection */
    private fun resolved(key: Key): Map<String, ModelTexture> {
        val merged = linkedMapOf<String, ModelTexture>()
        chain(key).forEach { model ->
            model.textures().layers().forEachIndexed { index, texture -> merged.putIfAbsent("layer$index", texture) }
            model.textures().variables().forEach { (name, texture) -> merged.putIfAbsent(name, texture) }
            model.textures().particle()?.let { merged.putIfAbsent("particle", it) }
        }

        return merged.mapNotNull { (name, texture) ->
            var current = texture
            var guard = 0
            while (guard++ < 16) {
                val reference = current.reference() ?: break
                current = merged[reference] ?: return@mapNotNull null
            }
            name to current
        }.toMap()
    }
}
