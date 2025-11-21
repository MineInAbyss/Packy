package com.mineinabyss.packy.helpers

import com.mineinabyss.idofront.resourcepacks.ResourcePacks
import com.mineinabyss.idofront.util.mapNotNullFast
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.atlas.Atlas
import team.unnamed.creative.atlas.AtlasSource
import team.unnamed.creative.atlas.SingleAtlasSource
import team.unnamed.creative.model.ModelTexture

object AtlasGenerator {
    fun generateAtlasFile(resourcePack: ResourcePack) {
        val textures = resourcePack.models().flatMap { model ->
            model.textures().layers().plus(model.textures().variables().values).plus(model.textures().particle())
        }.filterNotNull().mapNotNull(ModelTexture::key)

        val sources = textures.distinctBy { it.asString() }.mapNotNull { key ->
            if (ResourcePacks.vanillaResourcePack.texture(key) != null) return@mapNotNull null
            else AtlasSource.single(key)
        }.sortedBy { it.resource() }.distinct()

        val atlas = resourcePack.atlas(Atlas.BLOCKS)?.toBuilder()?.apply { sources.forEach(::addSource) }?.build() ?: Atlas.atlas(Atlas.BLOCKS, sources.distinct())

        atlas.addTo(resourcePack)
    }

    private fun addKey(keys: List<Key>, sources: MutableList<AtlasSource>) {
        keys.forEach { key ->
            if (ResourcePacks.vanillaResourcePack.texture(key) == null) sources.add(AtlasSource.single(key))
        }
    }
}