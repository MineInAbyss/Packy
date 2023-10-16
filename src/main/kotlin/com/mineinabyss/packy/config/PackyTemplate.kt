package com.mineinabyss.packy.config

import com.mojang.datafixers.util.Either
import kotlinx.serialization.Contextual
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.jvm.optionals.getOrDefault

@Serializable
data class PackyTemplate(
    val id: String,
    val default: Boolean = false,
    val forced: Boolean,
    @EncodeDefault(NEVER) val conflictsWith: Set<String> = setOf()
)

fun PackyTemplate.conflictsWith(template: PackyTemplate) =
    template.id in this.conflictsWith || this.id in template.conflictsWith

