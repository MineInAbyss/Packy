package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.idofront.messaging.broadcastVal
import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.messaging.logWarn
import com.mineinabyss.packy.config.packy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.div
import kotlin.io.path.pathString

object PackyDownloader {

    fun downloadTemplates() {
        runBlocking {
            packy.templates.filter { it.githubUrl != null }.map {
                async {
                    logWarn("Downloading ${it.id}-template from ${it.githubUrl}...")
                    downloadZipAndExtract(it.githubUrl!!, (packy.plugin.dataFolder.toPath() / "templates/${it.id}").pathString)
                    logSuccess("Successfully downloaded ${it.id}-template!")
                }
            }.awaitAll()
        }
    }

    fun downloadZipAndExtract(downloadUrl: String, destinationFolder: String) {
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
