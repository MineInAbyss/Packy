package com.mineinabyss.packy.helpers

import com.mineinabyss.idofront.messaging.logError
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object PackyDownloader {
    fun downloadZipAndExtract(owner: String, repository: String, branch: String, path: String, destinationFolder: String) {
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
        }
    }
}
