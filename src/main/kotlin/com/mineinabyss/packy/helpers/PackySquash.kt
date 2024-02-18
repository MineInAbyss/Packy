package com.mineinabyss.packy.helpers

import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.messaging.logInfo
import com.mineinabyss.idofront.messaging.logWarn
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.internal.writeJson
import okio.Path.Companion.toPath
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.metadata.pack.PackMeta
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension

object PackySquash {

    fun squashPack(resourcePack: ResourcePack) {
        val packSquash = packy.config.packSquash
        if (!packSquash.enabled) return
        if (packSquash.exePath.isEmpty()) return
        if (packSquash.settingsPath.isEmpty()) return

        val baseToml = File(packSquash.settingsPath).takeIf { it.exists() && it.isFile && it.extension == "toml" }
            ?: packy.plugin.dataFolder.resolve(packSquash.settingsPath)

        val packName = UUID.randomUUID().toString()
        val packDir = packy.plugin.dataFolder.resolve("packsquash").resolve(packName)
        MinecraftResourcePackWriter.minecraft().writeToDirectory(packDir, resourcePack)

        val toml = packDir.parentFile.resolve("$packName.toml")
        val tomlContent = baseToml.readText()
            .replace("pack_directory = .*".toRegex(), "pack_directory = '${packDir.absolutePath.replace("\\", "/")}'")
            .replace("output_file_path = .*".toRegex(), "output_file_path = '${packDir.absolutePath.replace("\\", "/") + ".zip"}'")
        toml.writeText(tomlContent)

        runCatching {
            logInfo("Squashing Packy-pack...")
            val processBuilder = ProcessBuilder(packSquash.exePath, toml.absolutePath.replace("\\", "/"))
            processBuilder.directory(packy.plugin.dataFolder)
            processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            // Read the output of the command
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var currentLine: String?
            while (reader.readLine().also { currentLine = it } != null) {
                val line = currentLine.takeUnless { it.isNullOrEmpty() } ?: continue
                when {
                    line.startsWith("!") -> logError(line)
                    line.startsWith("*") -> logWarn(line)
                    //else -> logInfo(line)
                }
            }
        }.onFailure {
            it.printStackTrace()
        }

        packDir.deleteRecursively()
    }

    fun squashPackyTemplate(template: PackyTemplate, templateID: String) {
        val packSquash = packy.config.packSquash
        if (!packSquash.enabled) return
        if (packSquash.exePath.isEmpty()) return
        if (packSquash.settingsPath.isEmpty()) return

        val templatePath = template.path.absolutePathString().replace("\\", "/").replace(".zip", "")
        val isZipTemplate = template.path.extension == "zip"
        val hasMcMeta = File(templatePath).listFiles()?.none { it.name == "pack.mcmeta"} == true

        if (isZipTemplate) unzip(template.path.toFile(), File(templatePath))
        if (!hasMcMeta) templatePath.toPath().resolve("pack.mcmeta").toFile().writeText("{\"pack\": {\"pack_format\": 16,\"description\": \"test\"}}")


        val baseToml = File(packSquash.settingsPath).takeIf { it.exists() && it.isFile && it.extension == "toml" }
            ?: packy.plugin.dataFolder.resolve(packSquash.settingsPath)
        val toml = File("$templatePath.toml")
        val tomlContent = baseToml.readText()
            .replace("pack_directory = .*".toRegex(), "pack_directory = '${templatePath}'")
            .replace("output_file_path = .*".toRegex(), "output_file_path = '${"$templatePath.zip"}'")
        toml.writeText(tomlContent)

        runCatching {
            val processBuilder = ProcessBuilder(packSquash.exePath, toml.absolutePath.replace("\\", "/"))
            processBuilder.directory(packy.plugin.dataFolder)
            processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            // Read the output of the command
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var currentLine: String?
            while (reader.readLine().also { currentLine = it } != null) {
                val line = currentLine.takeUnless { it.isNullOrEmpty() } ?: continue
                when {
                    line.startsWith("!") -> logError(line)
                    line.startsWith("*") -> logWarn(line)
                    //else -> logInfo(line)
                }
            }
        }.onFailure {
            it.printStackTrace()
        }

        if (isZipTemplate) File(templatePath).deleteRecursively()
        toml.delete()
    }
}