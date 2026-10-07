package com.mineinabyss.packy.helpers

import com.mineinabyss.packy.helpers.atlas.AtlasSide
import com.mineinabyss.packy.helpers.atlas.ModelGraph
import com.mineinabyss.packy.helpers.atlas.atlasClone
import com.mineinabyss.packy.helpers.atlas.cloneSprite
import com.mineinabyss.packy.helpers.atlas.isGenerated
import com.mineinabyss.packy.helpers.atlas.migratePalettes
import com.mineinabyss.packy.helpers.atlas.narrowedTo
import com.mineinabyss.packy.helpers.atlas.referencedModels
import com.mineinabyss.packy.helpers.atlas.remapModels
import com.mineinabyss.packy.helpers.atlas.withPng
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.atlas.Atlas
import team.unnamed.creative.atlas.AtlasSource
import team.unnamed.creative.atlas.PalettedPermutationsAtlasSource
import team.unnamed.creative.base.Writable
import team.unnamed.creative.metadata.pack.FormatVersion
import team.unnamed.creative.model.ModelTexture
import team.unnamed.creative.model.ModelTextures
import team.unnamed.creative.overlay.ResourceContainer
import team.unnamed.creative.texture.Texture
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object AtlasGenerator {

    private val MISSING_TEXTURE = Key.key("packy", "atlas/missing")

    /** Returns the textures item-models reach that exist nowhere, they are shown as the missing texture */
    fun generateAtlasFile(resourcePack: ResourcePack, format: FormatVersion): Set<Key> {
        val imported = takeImportedAtlases(resourcePack)
        val originalGraph = ModelGraph(resourcePack, format, imported)

        // A vanilla definition or blockstate has no owner, a remapped copy of it is written to the root pack
        val packStates = originalGraph.containers.flatMap { container -> container.blockStates().map { it to container } }.distinctBy { it.first.key() }
        val packDefinitions = originalGraph.containers.flatMap { container -> container.items().map { it to container } }.distinctBy { it.first.key() }
        val packStateKeys = packStates.mapTo(mutableSetOf()) { it.first.key() }
        val packDefinitionKeys = packDefinitions.mapTo(mutableSetOf()) { it.first.key() }
        val states = packStates + originalGraph.vanilla.blockStates()
            .filter { it.key() !in packStateKeys && it.referencedModels().any(originalGraph::touchesPack) }
            .map { it to null }
        val definitions = packDefinitions + originalGraph.vanilla.items()
            .filter { it.key() !in packDefinitionKeys && it.model().referencedModels().any(originalGraph::touchesPack) }
            .map { it to null }

        val stateRoots = states.flatMapTo(mutableSetOf()) { it.first.referencedModels() }
        val itemRootKeys = definitions.flatMapTo(mutableSetOf()) { it.first.model().referencedModels() }
        repairFaceReferences(originalGraph, stateRoots + itemRootKeys)
        val graph = ModelGraph(resourcePack, format, imported)

        val stateTextures = stateRoots.flatMapTo(mutableSetOf(), graph::textureKeys)
        // The client validates each model an item-definition reaches on its own, so each picks the atlas its textures already sit in
        val itemRootSides = itemRootKeys.associateWith { root ->
            val textures = graph.textureKeys(root)
            when {
                textures.any { graph.locked(AtlasSide.ITEMS, it) } -> AtlasSide.ITEMS
                textures.isNotEmpty() && textures.all { graph.locked(AtlasSide.BLOCKS, it) || it in stateTextures } -> AtlasSide.BLOCKS
                else -> AtlasSide.ITEMS
            }
        }
        val itemRoots = itemRootSides.filterValues { it == AtlasSide.ITEMS }.keys
        val blockRoots = stateRoots + itemRootSides.filterValues { it == AtlasSide.BLOCKS }.keys
        val itemsNeeded = itemRoots.flatMapTo(mutableSetOf(), graph::textureKeys)
        val blocksNeeded = blockRoots.flatMapTo(mutableSetOf(), graph::textureKeys)

        // Re-emitted generated sources, one per cloned sprite
        val clonedSources = AtlasSide.entries.associateWith { mutableListOf<AtlasSource>() }
        fun cloneFor(side: AtlasSide, texture: Key): Key? {
            graph.texture(texture)?.let { file ->
                return texture.atlasClone(side).also { resourcePack.texture(file.toBuilder().key(it.withPng()).build()) }
            }
            val (clone, source) = imported.values.flatten().firstNotNullOfOrNull { it.cloneSprite(texture, side, graph) } ?: return null
            clonedSources.getValue(side) += source
            return clone
        }
        fun vanillaOnly(texture: Key) = graph.packTexture(texture) == null && graph.vanillaTexture(texture) != null

        // Shared pack-textures stay with blocks and the item side clones them, a sprite only vanilla generates stays where vanilla put it
        val blockRemap = blocksNeeded
            .filter { !graph.locked(AtlasSide.BLOCKS, it) && (graph.locked(AtlasSide.ITEMS, it) || vanillaOnly(it)) }
            .mapNotNull { texture -> cloneFor(AtlasSide.BLOCKS, texture)?.let { texture to it } }.toMap()
        val itemRemap = mutableMapOf<Key, Key>()
        val missing = mutableSetOf<Key>()
        itemsNeeded.forEach { texture ->
            when {
                !graph.exists(texture) -> missing += texture
                graph.locked(AtlasSide.ITEMS, texture) -> {}
                graph.locked(AtlasSide.BLOCKS, texture) || texture in blocksNeeded || vanillaOnly(texture) ->
                    cloneFor(AtlasSide.ITEMS, texture)?.let { itemRemap[texture] = it } ?: missing.add(texture)
            }
        }
        // An item-model reaching a missing texture gets the missing sprite from blocks and fails as a whole, an items-side copy keeps the rest of it rendering
        if (missing.isNotEmpty()) {
            resourcePack.texture(Texture.texture(MISSING_TEXTURE.withPng(), missingTexture))
            missing.forEach { itemRemap[it] = MISSING_TEXTURE }
        }

        // Changed models are cloned rather than edited, the original may also be a parent the other side inherits from
        fun cloneModels(roots: Set<Key>, remap: Map<Key, Key>, side: AtlasSide) = roots.mapNotNull { root ->
            if (graph.textureKeys(root).none { it in remap }) return@mapNotNull null
            val model = graph.model(root) ?: return@mapNotNull null
            val clone = root.atlasClone(side)
            resourcePack.model(model.toBuilder().key(clone).textures(graph.flattened(root, remap)).build())
            root to clone
        }.toMap()
        val itemModelRemap = cloneModels(itemRoots, itemRemap, AtlasSide.ITEMS)
        val blockModelRemap = cloneModels(blockRoots, blockRemap, AtlasSide.BLOCKS)

        val definitionRemap = itemRootSides.mapNotNull { (root, side) ->
            (if (side == AtlasSide.ITEMS) itemModelRemap[root] else blockModelRemap[root])?.let { root to it }
        }.toMap()
        definitions.forEach { (definition, owner) ->
            if (definition.model().referencedModels().none { it in definitionRemap }) return@forEach
            (owner ?: resourcePack).item(definition.remapModels(definitionRemap))
        }
        states.forEach { (state, owner) ->
            if (state.referencedModels().none { it in blockModelRemap }) return@forEach
            (owner ?: resourcePack).blockState(state.remapModels(blockModelRemap))
        }

        writeAtlas(graph, AtlasSide.ITEMS, itemsNeeded.mapTo(mutableSetOf()) { itemRemap[it] ?: it }, imported, clonedSources)
        writeAtlas(graph, AtlasSide.BLOCKS, blocksNeeded.mapTo(mutableSetOf()) { blockRemap[it] ?: it }, imported, clonedSources)
        return missing
    }

    /**
     * Atlas-sources concatenate across the root and overlays, so any imported atlas left behind would add its sprites back.
     * Their generated-sprite sources are kept, those sprites have no file a single-source could name
     */
    private fun takeImportedAtlases(resourcePack: ResourcePack): Map<AtlasSide, List<AtlasSource>> {
        val containers = listOf<ResourceContainer>(resourcePack) + resourcePack.overlays()
        return AtlasSide.entries.associateWith { side ->
            containers.flatMap { container ->
                container.atlas(side.atlas)?.sources().orEmpty().also { container.removeAtlas(side.atlas) }
            }.filter(AtlasSource::isGenerated).distinct()
        }
    }

    // A face-reference resolving to nothing renders the missing sprite from blocks, so it is pointed at the model's first texture
    private fun repairFaceReferences(graph: ModelGraph, roots: Set<Key>) = roots.forEach { root ->
        val owner = graph.owner(root) ?: return@forEach
        val unresolved = graph.unresolvedFaceReferences(root).ifEmpty { return@forEach }
        val textures = graph.effectiveTextures(root)
        val fallback = textures.entries.firstOrNull { it.key != "particle" }?.value ?: textures["particle"] ?: return@forEach
        val model = owner.model(root) ?: return@forEach

        val repaired = ModelTextures.builder()
            .layers(model.textures().layers())
            .particle(model.textures().particle())
            .variables(model.textures().variables() + unresolved.associateWith { ModelTexture.ofKey(fallback.key()) })
            .build()
        owner.model(model.toBuilder().textures(repaired).build())
    }

    // Sprites a vanilla or imported source already stitches into this atlas need no single
    private fun writeAtlas(
        graph: ModelGraph, side: AtlasSide, textures: Set<Key>,
        imported: Map<AtlasSide, List<AtlasSource>>, clonedSources: Map<AtlasSide, List<AtlasSource>>,
    ) {
        val singles = textures.filter { !graph.locked(side, it) && graph.packTexture(it) != null }.sortedBy(Key::asString).map(AtlasSource::single)
        val generated = (imported.getValue(side) + clonedSources.getValue(side)).mapNotNull { it.narrowedTo(textures) }.distinct()
        generated.filterIsInstance<PalettedPermutationsAtlasSource>().forEach { it.migratePalettes(graph) }

        val sources = singles + generated
        if (sources.isNotEmpty()) graph.resourcePack.atlas(Atlas.atlas(side.atlas, sources))
    }

    private val missingTexture: Writable by lazy {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val magenta = Color(0xF8, 0x00, 0xF8).rgb
        for (x in 0 until 16) for (y in 0 until 16) image.setRGB(x, y, if ((x < 8) == (y < 8)) magenta else Color.BLACK.rgb)
        Writable.bytes(ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray())
    }
}
