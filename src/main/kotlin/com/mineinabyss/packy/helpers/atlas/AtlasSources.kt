package com.mineinabyss.packy.helpers.atlas

import net.kyori.adventure.key.Key
import team.unnamed.creative.atlas.Atlas
import team.unnamed.creative.atlas.AtlasSource
import team.unnamed.creative.atlas.DirectoryAtlasSource
import team.unnamed.creative.atlas.PalettedPermutationsAtlasSource
import team.unnamed.creative.atlas.SingleAtlasSource
import team.unnamed.creative.atlas.UnstitchAtlasSource

internal enum class AtlasSide(val atlas: Key) { ITEMS(Atlas.ITEMS), BLOCKS(Atlas.BLOCKS) }

internal fun Key.atlasClone(side: AtlasSide) = Key.key("packy", "atlas/${side.name.lowercase()}/${namespace()}/${value()}")
internal fun Key.withPng() = Key.key(namespace(), "${value()}.png")

/** Sprites the client generates, they have no file a single-source could name */
internal fun AtlasSource.isGenerated() = this is PalettedPermutationsAtlasSource || this is UnstitchAtlasSource

internal fun AtlasSource.provides(sprite: Key, fileExists: (Key) -> Boolean): Boolean = when (this) {
    is SingleAtlasSource -> (sprite() ?: resource()) == sprite
    is DirectoryAtlasSource -> sprite.value().startsWith("${source()}/") &&
        prefix() + sprite.value().removePrefix("${source()}/") == sprite.value() && fileExists(sprite)
    is PalettedPermutationsAtlasSource -> baseOf(sprite) != null
    is UnstitchAtlasSource -> regions().any { it.sprite() == sprite }
    else -> false
}

internal fun PalettedPermutationsAtlasSource.baseOf(sprite: Key): Key? = textures().firstOrNull { base ->
    base.namespace() == sprite.namespace() && permutations().keys.any { sprite.value() == base.value() + separator() + it }
}

internal fun AtlasSource.narrowedTo(used: Set<Key>): AtlasSource? = when (this) {
    is PalettedPermutationsAtlasSource -> {
        fun sprite(base: Key, permutation: String) = Key.key(base.namespace(), base.value() + separator() + permutation)
        val bases = textures().filter { base -> permutations().keys.any { sprite(base, it) in used } }
        val permutations = permutations().filterKeys { permutation -> bases.any { sprite(it, permutation) in used } }
        bases.takeIf { it.isNotEmpty() }?.let { AtlasSource.palettedPermutations(it, paletteKey(), permutations, separator()) }
    }
    is UnstitchAtlasSource -> regions().filter { it.sprite() in used }.takeIf { it.isNotEmpty() }
        ?.let { AtlasSource.unstitch(resource(), it, divisor()) }
    else -> null
}

internal fun AtlasSource.cloneSprite(sprite: Key, side: AtlasSide, graph: ModelGraph): Pair<Key, AtlasSource>? = when (this) {
    is PalettedPermutationsAtlasSource -> baseOf(sprite)?.let { base ->
        val baseTexture = graph.texture(base) ?: return null
        val clonedBase = base.atlasClone(side)
        graph.resourcePack.texture(baseTexture.toBuilder().key(clonedBase.withPng()).build())
        Key.key(clonedBase.namespace(), clonedBase.value() + sprite.value().removePrefix(base.value())) to
            AtlasSource.palettedPermutations(listOf(clonedBase), paletteKey(), permutations(), separator())
    }
    is UnstitchAtlasSource -> regions().firstOrNull { it.sprite() == sprite }?.let { region ->
        val clone = sprite.atlasClone(side)
        clone to AtlasSource.unstitch(resource(), listOf(UnstitchAtlasSource.Region.region(clone, region.position(), region.dimensions())), divisor())
    }
    else -> null
}

// The client loads palettes from textures/palettes, packs written for older versions keep them beside their textures
internal fun PalettedPermutationsAtlasSource.migratePalettes(graph: ModelGraph) {
    (permutations().values + paletteKey()).forEach { palette ->
        val current = Key.key(palette.namespace(), "palettes/${palette.value()}")
        if (graph.texture(current) != null) return@forEach
        val legacy = graph.texture(palette) ?: return@forEach
        graph.resourcePack.texture(legacy.toBuilder().key(current.withPng()).build())
    }
}
