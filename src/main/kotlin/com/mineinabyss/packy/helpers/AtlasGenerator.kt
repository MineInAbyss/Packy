package com.mineinabyss.packy.helpers

import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.packy.helpers.atlas.ModelGraph
import com.mineinabyss.packy.helpers.atlas.referencedModels
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.atlas.Atlas
import team.unnamed.creative.atlas.AtlasSource
import team.unnamed.creative.overlay.ResourceContainer

object AtlasGenerator {

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

        resourcePack.writeAtlas(Atlas.ITEMS, itemRoots.flatMapTo(mutableSetOf(), graph::effectiveTextures))
        resourcePack.writeAtlas(Atlas.BLOCKS, blockRoots.flatMapTo(mutableSetOf(), graph::effectiveTextures) + looseTextures)
    }

    /** Both atlases can stitch the same texture, so one that both sides reach simply ends up in each */
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
