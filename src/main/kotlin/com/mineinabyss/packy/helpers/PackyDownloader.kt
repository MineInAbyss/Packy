package com.mineinabyss.packy.helpers

import com.google.gson.JsonParser
import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.messaging.logWarn
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
import kotlin.io.path.div
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.io.path.readLines

object PackyDownloader {

    fun updateGithubTemplates(template: PackyTemplate) {
        val hashFile = packy.plugin.dataFolder.toPath() / "templates/${template.id}" / "localHash.txt"
        if (hashFile.notExists()) hashFile.toFile().createNewFile()

        val latestCommitHash = getLatestCommitSha(template.githubUrl ?: return)
        val localCommitHash = hashFile.readLines().find { it.matches("hash=.*".toRegex()) }

        when {
            localCommitHash == null || localCommitHash != latestCommitHash ->
                downloadAndExtractTemplate(template)
        }
    }

    private fun getLatestCommitSha(githubUrl: String): String? {
        val (owner, repository) = githubUrl.substringAfter("github.com/").split("/", limit = 3)
        val (branch, path) = githubUrl.substringAfter("/tree/").split("/", limit = 3)
        val apiUrl = "https://api.github.com/repos/$owner/$repository/commits?path=$path&sha=$branch"
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
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
            packy.templates.filter { it.githubUrl != null }.map {
                async {
                    logWarn("Downloading ${it.id}-template from ${it.githubUrl}...")
                    updateGithubTemplates(it)
                    logSuccess("Successfully downloaded ${it.id}-template!")
                }
            }.awaitAll()
        }
    }

    fun downloadAndExtractTemplate(template: PackyTemplate) {
        val downloadUrl = template.githubUrl ?: return
        val destinationFolder = (packy.plugin.dataFolder.toPath() / "templates/${template.id}").pathString
        val (owner, repository) = downloadUrl.substringAfter("github.com/").split("/", limit = 3)
        val (branch, path) = downloadUrl.substringAfter("/tree/").split("/", limit = 3)
        val zipUrl = "https://github.com/$owner/$repository/archive/$branch.zip"

        runCatching {
            val inputStream = URL(zipUrl).openStream()
            val zipStream = ZipInputStream(inputStream)

            var entry: ZipEntry? = zipStream.nextEntry
            while (entry != null) {
                val entryName = entry.name
                if (entryName.startsWith("$repository-$branch/$path") && !entry.isDirectory) {
                    val filePath = Paths.get(destinationFolder, entryName.substring("$repository-$branch/$path".length))
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
