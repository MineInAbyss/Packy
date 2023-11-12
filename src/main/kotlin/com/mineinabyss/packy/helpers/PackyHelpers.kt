package com.mineinabyss.packy.helpers

import com.google.gson.JsonParser
import com.mineinabyss.packy.config.PackyConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import team.unnamed.creative.metadata.pack.PackMeta
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.SortedSet
import kotlin.io.path.Path

fun File.toPackMeta() : PackMeta? {
    val json = JsonParser.parseString(this.readText(StandardCharsets.UTF_8)).asJsonObject.getAsJsonObject("pack") ?: return null
    val format = json.getAsJsonPrimitive("pack_format").asNumber?.toInt() ?: return null
    val description = json.getAsJsonPrimitive("description").asString ?: return null
    return PackMeta.of(format, description)
}

typealias TemplateIds = SortedSet<String>
