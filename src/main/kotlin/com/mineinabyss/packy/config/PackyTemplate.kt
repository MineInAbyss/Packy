package com.mineinabyss.packy.config

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.messaging.logSuccess
import com.mojang.datafixers.util.Either
import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import kotlin.io.path.div
import kotlin.jvm.optionals.getOrDefault

@Serializable
data class PackyTemplate(
    val id: String,
    val default: Boolean = false,
    val forced: Boolean,
    @EncodeDefault(NEVER) val conflictsWith: Set<String> = setOf(),
    @SerialName("url") @EncodeDefault(NEVER) val _url: String? = null
)

fun PackyTemplate.conflictsWith(template: PackyTemplate) =
    template.id in this.conflictsWith || this.id in template.conflictsWith

