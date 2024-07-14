package com.mineinabyss.packy.components

import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.TemplateIds
import net.kyori.adventure.resource.ResourcePackInfo
import team.unnamed.creative.BuiltResourcePack
import java.net.URI
import java.util.*

data class PackyPack(val resourcePackInfo: ResourcePackInfo, val builtPack: BuiltResourcePack) {
    constructor(builtPack: BuiltResourcePack, templateIds: TemplateIds) :
            this(ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(builtPack.hash().toByteArray()), URI.create(
                packy.config.server.publicUrl(builtPack.hash(), templateIds)), builtPack.hash()), builtPack)

}