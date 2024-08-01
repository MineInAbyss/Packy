package com.mineinabyss.packy.helpers

import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import okhttp3.Response
import org.apache.commons.io.IOUtils
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackReader
import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively

fun File.readPack(): ResourcePack? {
    return runCatching {
        when {
            !exists() -> null
            isDirectory && !listFiles().isNullOrEmpty() -> packy.reader.readFromDirectory(this)
            extension == "zip" -> packy.reader.readFromZipFile(this)
            else -> null
        }
    }.onFailure { packy.logger.w(this.name + ": " + it.message) }.getOrNull()
}

@OptIn(ExperimentalPathApi::class)
fun Response.downloadZipFromGithubResponse(vararg templates: PackyTemplate) {
    // Read the entire response body into a byte array
    val responseBody = body?.byteStream()?.use { inputStream ->
        ByteArrayOutputStream().apply {
            inputStream.copyTo(this)
        }.toByteArray()
    } ?: run {
        packy.logger.e("${templates.joinToString { it.name }} has no response body, skipping...")
        return
    }

    templates.forEach { template ->
        val githubDownload = template.githubDownload
        if (githubDownload == null) {
            packy.logger.e("${template.name} has no githubDownload, skipping...")
            return@forEach
        }

        val (owner, repo, _, subPath) = githubDownload

        // Create a new ZipInputStream from the byte array for each template
        val zipStream = ZipInputStream(ByteArrayInputStream(responseBody))

        runCatching {
            // Ensure the template path is deleted before extraction
            template.path.deleteRecursively()

            // Use a ZipOutputStream to write the extracted files
            ZipOutputStream(FileOutputStream(template.path.toFile())).use { zipOutputStream ->
                var entry = zipStream.nextEntry

                while (entry != null) {
                    if (entry.name.startsWith("$owner-$repo", ignoreCase = true) && !entry.isDirectory) {
                        val entryName = entry.name.substringAfter("/${subPath?.let { "$it/" } ?: ""}")
                        if (entryName != entry.name) {
                            zipOutputStream.putNextEntry(ZipEntry(entryName))

                            val buffer = ByteArray(1024)
                            var len: Int
                            while (zipStream.read(buffer).also { len = it } > 0) {
                                zipOutputStream.write(buffer, 0, len)
                            }

                            zipOutputStream.closeEntry()
                        }
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
        }.onFailure { it.printStackTrace() }
        zipStream.close() // Ensure the ZipInputStream is closed properly
    }
}

typealias TemplateIds = SortedSet<String>

fun unzip(zipFile: File, destDir: File) {
    runCatching {
        destDir.deleteRecursively()
        destDir.mkdirs() // Create the destination directory

        ZipInputStream(FileInputStream(zipFile)).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry

            while (zipEntry != null) {
                val entryFile = File(destDir, zipEntry.name)

                // Create parent directories if they don't exist
                entryFile.parentFile?.mkdirs()

                if (!zipEntry.isDirectory) {
                    FileOutputStream(entryFile).use { outputStream ->
                        val buffer = ByteArray(1024)
                        var len: Int
                        while (zipInputStream.read(buffer).also { len = it } > 0) {
                            outputStream.write(buffer, 0, len)
                        }
                    }
                }
                zipEntry = zipInputStream.nextEntry
            }
        }
    }.onFailure {
        it.printStackTrace()
    }
}
