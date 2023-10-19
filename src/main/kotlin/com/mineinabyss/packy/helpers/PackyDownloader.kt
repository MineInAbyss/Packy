package com.mineinabyss.packy.helpers

import com.google.gson.JsonParser
import com.mineinabyss.idofront.messaging.*
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.*

object PackyDownloader {

    fun updateGithubTemplates(template: PackyTemplate): Boolean {
        val hashFile = packy.plugin.dataFolder.toPath() / "templates/${template.id}" / "localHash.txt"
        hashFile.createParentDirectories()
        if (hashFile.notExists()) hashFile.createFile()

        val latestCommitHash = getLatestCommitSha(template.githubUrl ?: return false)
        val localCommitHash = hashFile.readLines().find { it.matches("hash=.*".toRegex()) }?.substringAfter("=")

        when {
            localCommitHash == null || localCommitHash != latestCommitHash ->
            {
                downloadAndExtractTemplate(template)
                hashFile.writeLines("hash=$latestCommitHash".lineSequence())
                logInfo("Updated localHash.txt for ${template.id}")
            }
            else -> logSuccess("Skipping download for ${template.id}, no changes applied to remote")
        }
        return localCommitHash == null || localCommitHash != latestCommitHash
    }

    private fun getLatestCommitSha(githubUrl: String): String? {
        val (owner, repository) = githubUrl.substringAfter("github.com/").split("/", limit = 3)
        val branch = githubUrl.substringAfter("tree/").substringBefore("/").substringBefore("?").removeSuffix("/")
        val path = (githubUrl.substringAfter("tree/").substringAfter(branch).substringBefore("?") + "/assets").removePrefix("/")
        val apiUrl = "https://api.github.com/repos/$owner/$repository/commits?path=$path&sha=$branch"
        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "token ${packy.accessToken.token}")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val reader = connection.inputStream.bufferedReader()
            val jsonArray = JsonParser.parseReader(reader).asJsonArray
            if (jsonArray.size() > 0) {
                val latestCommit = jsonArray[0].asJsonObject
                return latestCommit.get("sha").asString
            }
        }

        return null
    }

    fun downloadTemplates() {
        runBlocking {
            packy.templates.entries.filter { it.value.githubUrl != null }.map { (id, template) ->
                async {
                    logWarn("Downloading ${id}-template from ${template.githubUrl}...")
                    if (updateGithubTemplates(template))
                        logSuccess("Successfully downloaded ${id}-template!")
                }
            }.awaitAll()
        }
    }

    fun downloadAndExtractTemplate(template: PackyTemplate) {
        val downloadUrl = template.githubUrl?.substringBeforeLast("?")?.removeSuffix("/assets")?.removeSuffix("/") ?: return
        val destinationFolder = packy.plugin.dataFolder.toPath() / "templates" / template.id / "assets"
        val (owner, repository) = downloadUrl.substringAfter("github.com/").split("/", limit = 3)
        val branch = downloadUrl.substringAfter("tree/").substringBefore("/").substringBefore("?").removeSuffix("/")
        val path = (downloadUrl.substringAfter("tree/").substringAfter(branch).substringBefore("?") + "/assets").removePrefix("/")
        val zipUrl = "https://github.com/$owner/$repository/archive/$branch.zip"

        runCatching {
            destinationFolder.toFile().deleteRecursively()
            val connection = URL(zipUrl).openConnection()
            connection.setRequestProperty("Authorization", "token ${ packy.accessToken.token}")
            val zipStream = ZipInputStream(connection.getInputStream())

            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("$repository-$branch/$path", true) && !entry.isDirectory) {
                    val fileName = entry.name.substring("$repository-$branch/$path".length).removePrefix("assets/")
                    val filePath = Paths.get(destinationFolder.pathString, fileName)
                    Files.createDirectories(filePath.parent)
                    FileOutputStream(filePath.toFile()).use { output ->
                        val buffer = ByteArray(1024)
                        var len: Int
                        while (zipStream.read(buffer).also { len = it } > 0) {
                            output.write(buffer, 0, len)
                        }
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()

        }.onFailure {
            logError("Error downloading repository: ${it.message}")
            it.printStackTrace()
        }
    }
}
