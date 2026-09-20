package com.mineinabyss.packy.helpers

import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.packy.helpers.atlas.ModelGraph
import com.mineinabyss.packy.helpers.atlas.referencedModels
import com.mineinabyss.packy.helpers.atlas.remapModels
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.atlas.Atlas
import team.unnamed.creative.atlas.AtlasSource
import team.unnamed.creative.overlay.ResourceContainer
import team.unnamed.creative.texture.Texture

object AtlasGenerator {

    /** Namespaced under the texture itself so a block-side copy cannot collide with a real pack texture */
    private const val BLOCK_CLONE_PREFIX = "packy/atlas/block/"

    /**
     * 1.21.11 split the unified blocks-atlas, a texture an item-definition reaches now resolves from the
     * items-atlas and one a blockstate reaches from the blocks-atlas. Writing everything to blocks, as this
     * used to, leaves every item-texture unresolvable and the client draws it as missing.
     *
     * Atlas-sources concatenate across packs rather than the nearest one winning, so these sources add to
     * the split atlases an imported pack ships in its overlays instead of replacing them.
     */
    fun generateAtlasFile(resourcePack: ResourcePack) {
        val graph = ModelGraph(resourcePack)
        val containers = resourcePack.containers()

        val itemRoots = containers.flatMap(ResourceContainer::items)
            .flatMapTo(mutableSetOf()) { it.model().referencedModels() }
        val blockRoots = containers.flatMap(ResourceContainer::blockStates)
            .flatMapTo(mutableSetOf()) { it.referencedModels() }

        // Models nothing reaches are rendered as entities or handed to a plugin directly, they worked off the
        // unified atlas before the split so they stay on the block-side
        val reached = (itemRoots + blockRoots).flatMapTo(mutableSetOf(), graph::chainKeys)
        val looseTextures = containers.flatMap(ResourceContainer::models)
            .filterNot { it.key() in reached }
            .flatMapTo(mutableSetOf()) { graph.effectiveTextures(it.key()) }

        val itemTextures = itemRoots.flatMapTo(mutableSetOf(), graph::effectiveTextures)
        val blockTextures = blockRoots.flatMapTo(mutableSetOf(), graph::effectiveTextures) + looseTextures

        // A sprite is stitched into exactly one atlas. Listing it in both makes the client bind it to
        // whichever it loads first and reject every block-model reaching it from the other side, so a
        // texture both sides need is copied under a block-only key and the blockstates follow the copy
        val remap = blockTextures.intersect(itemTextures)
            .filter { resourcePack.hasTexture(it) }
            .associateWith { Key.key(it.namespace(), "$BLOCK_CLONE_PREFIX${it.value()}") }
        remap.forEach { (original, clone) -> resourcePack.cloneTexture(original, clone) }
        resourcePack.rebindBlockModels(graph, containers, blockRoots, itemRoots, remap)

        resourcePack.writeAtlas(Atlas.ITEMS, itemTextures)
        resourcePack.writeAtlas(Atlas.BLOCKS, blockTextures.mapTo(mutableSetOf()) { remap[it] ?: it })
    }

    /** Copies a texture, and whatever .mcmeta drives its animation, onto the block-side key */
    private fun ResourcePack.cloneTexture(original: Key, clone: Key) {
        val texture = texture(original) ?: texture(original.withPng()) ?: return
        texture(Texture.texture(clone.withPng(), texture.data(), texture.meta()))
    }

    /**
     * Points the models a blockstate reaches at the cloned textures. Textures are flattened onto the
     * model itself, the parent is kept since that is what carries the geometry.
     *
     * A model an item-definition also reaches cannot be rewritten in place, that would drag the item
     * side onto block-only textures, so it gets a clone and the blockstates follow it.
     */
    private fun ResourcePack.rebindBlockModels(
        graph: ModelGraph,
        containers: List<ResourceContainer>,
        blockRoots: Set<Key>,
        itemRoots: Set<Key>,
        remap: Map<Key, Key>,
    ) {
        if (remap.isEmpty()) return
        val itemChain = itemRoots.flatMapTo(mutableSetOf(), graph::chainKeys)
        val modelRemap = mutableMapOf<Key, Key>()

        blockRoots.forEach { root ->
            if (graph.effectiveTextures(root).none { it in remap }) return@forEach
            val model = graph.model(root) ?: return@forEach
            val rebound = model.toBuilder().textures(graph.flattened(root, remap))

            if (root !in itemChain) return@forEach model(rebound.build())

            val clone = Key.key(root.namespace(), "$BLOCK_CLONE_PREFIX${root.value()}")
            model(rebound.key(clone).build())
            modelRemap[root] = clone
        }

        if (modelRemap.isEmpty()) return
        containers.forEach { container ->
            container.blockStates().toList().forEach { blockState ->
                if (blockState.referencedModels().none { it in modelRemap }) return@forEach
                container.blockState(blockState.remapModels(modelRemap))
            }
        }
    }

    private fun Key.withPng() = Key.key(namespace(), "${value()}.png")

    private fun ResourcePack.writeAtlas(atlasKey: Key, textures: Set<Key>) {
        // A source naming a texture the pack does not have makes the client drop the whole definition
        val singles = textures.filter { !ResourcePacks.vanillaResourcePack.hasTexture(it) && hasTexture(it) }
            .sortedBy(Key::asString).map(AtlasSource::single)
        val sources = (atlas(atlasKey)?.sources().orEmpty() + singles).distinct()

        if (sources.isNotEmpty()) atlas(Atlas.atlas(atlasKey, sources))
    }

    private fun ResourcePack.containers() = listOf<ResourceContainer>(this) + overlays()

    // Textures are keyed with and without the extension depending on how the pack was read
    private fun ResourcePack.hasTexture(key: Key) = containers().any {
        it.texture(key) != null || it.texture(Key.key(key.namespace(), "${key.value()}.png")) != null
    }
}
