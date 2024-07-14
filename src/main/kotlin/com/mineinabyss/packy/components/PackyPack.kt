package com.mineinabyss.packy.components

import net.kyori.adventure.resource.ResourcePackInfo
import team.unnamed.creative.BuiltResourcePack
import java.net.URI
import java.util.*

data class PackyPack(val resourcePackInfo: ResourcePackInfo, val builtPack: BuiltResourcePack) {
    constructor(hash: String, url: String, builtPack: BuiltResourcePack) :
            this(ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(hash.toByteArray()), URI.create(url), hash), builtPack)

}