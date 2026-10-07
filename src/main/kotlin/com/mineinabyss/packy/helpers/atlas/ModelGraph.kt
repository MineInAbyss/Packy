package com.mineinabyss.packy.helpers.atlas

import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.atlas.AtlasSource
import team.unnamed.creative.metadata.pack.FormatVersion
import team.unnamed.creative.model.Model
import team.unnamed.creative.model.ModelTexture
import team.unnamed.creative.model.ModelTextures
import team.unnamed.creative.overlay.ResourceContainer
import team.unnamed.creative.texture.Texture

/**
 * Models and textures as a client on [format] resolves them, overlays it does not load are ignored.
 * Lookups are cached, so a graph only describes the pack as it was when the graph was made
 */
internal class ModelGraph(val resourcePack: ResourcePack, format: FormatVersion, imported: Map<AtlasSide, List<AtlasSource>>) {

    val vanilla: ResourcePack = ResourcePacks.vanillaResourcePack

    /** Highest priority first, the client lets a later overlay-entry shadow an earlier one and every overlay shadow the root */
    val containers: List<ResourceContainer> = resourcePack.overlaysMeta()?.entries().orEmpty()
        .filter { it.formats().isInRange(format) }
        .map { it.directory() }
        .asReversed().distinct()
        .mapNotNull(resourcePack::overlay)
        .plus(resourcePack)

    private val atlasSources = AtlasSide.entries.associateWith { vanilla.atlas(it.atlas)?.sources().orEmpty() + imported[it].orEmpty() }
    private val modelCache = mutableMapOf<Key, Model?>()
    private val chainCache = mutableMapOf<Key, List<Model>>()
    private val textureCache = mutableMapOf<Key, Map<String, ModelTexture>>()

    fun packTexture(key: Key): Texture? = containers.firstNotNullOfOrNull { it.texture(key) ?: it.texture(key.withPng()) }
    fun vanillaTexture(key: Key): Texture? = vanilla.texture(key) ?: vanilla.texture(key.withPng())
    fun texture(key: Key): Texture? = packTexture(key) ?: vanillaTexture(key)

    /** Vanilla's sources or an imported generated sprite already put this sprite in [side], wherever the file comes from */
    fun locked(side: AtlasSide, key: Key) = atlasSources.getValue(side).any { it.provides(key) { texture(it) != null } }

    fun exists(key: Key) = texture(key) != null || AtlasSide.entries.any { locked(it, key) }

    fun model(key: Key): Model? = modelCache.getOrPut(key) {
        containers.firstNotNullOfOrNull { it.model(key) } ?: vanilla.model(key)
    }

    /** The container whose copy of the model the client loads, null when only vanilla has it */
    fun owner(key: Key): ResourceContainer? = containers.firstOrNull { it.model(key) != null }

    fun chain(key: Key): List<Model> = chainCache.getOrPut(key) {
        val chain = mutableListOf<Model>()
        val seen = mutableSetOf<Key>()
        var current = model(key)
        while (current != null && seen.add(current.key())) {
            chain += current
            current = current.parent()?.let(::model)
        }
        chain
    }

    fun touchesPack(key: Key) = chain(key).any { owner(it.key()) != null }

    /** Slot name to the texture it ends up at, the child winning over its parents */
    fun effectiveTextures(key: Key): Map<String, ModelTexture> = textureCache.getOrPut(key) {
        val merged = linkedMapOf<String, ModelTexture>()
        chain(key).forEach { model ->
            model.textures().layers().forEachIndexed { index, texture -> merged.putIfAbsent("layer$index", texture) }
            model.textures().variables().forEach { (name, texture) -> merged.putIfAbsent(name, texture) }
            model.textures().particle()?.let { merged.putIfAbsent("particle", it) }
        }

        merged.entries.mapNotNull { (name, texture) ->
            var resolved = texture
            var hops = 0
            // Bounded so a #a -> #b -> #a cycle cannot loop forever
            while (hops++ < 16) resolved = merged[resolved.reference() ?: break] ?: return@mapNotNull null
            resolved.takeIf { it.key() != null }?.let { name to it }
        }.toMap(linkedMapOf())
    }

    fun textureKeys(key: Key): List<Key> = effectiveTextures(key).values.mapNotNull(ModelTexture::key)

    /** Face-references of the elements this model renders that resolve to no texture, Blockbench writes `#missing` for unassigned faces */
    fun unresolvedFaceReferences(key: Key): Set<String> {
        val elements = chain(key).firstOrNull { it.elements().isNotEmpty() }?.elements() ?: return emptySet()
        val textures = effectiveTextures(key)
        return elements.flatMap { it.faces().values }
            .mapNotNull { face -> face.texture().takeIf { it.startsWith("#") }?.removePrefix("#") }
            .filterTo(mutableSetOf()) { it !in textures }
    }

    /** Inheritance and `#references` collapsed onto the model itself, so a clone's textures cannot be changed through its parent */
    fun flattened(key: Key, remap: Map<Key, Key>): ModelTextures {
        val entries = effectiveTextures(key).mapValues { (_, texture) ->
            remap[texture.key()]?.let { ModelTexture.ofKey(it, texture.forceTranslucent()) } ?: texture
        }
        // creative reads layers back as a list, so they are always layer0 to layerN
        val layers = entries.keys.filter { LAYER.matches(it) }.sortedBy { it.removePrefix("layer").toInt() }
        return ModelTextures.builder()
            .layers(layers.map(entries::getValue))
            .particle(entries["particle"])
            .variables(entries.filterKeys { it != "particle" && it !in layers })
            .build()
    }

    private companion object {
        val LAYER = Regex("layer\\d+")
    }
}
