package com.mineinabyss.packy.helpers

import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.packy.config.PackyTemplate
import okhttp3.Response
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.deleteIfExists

fun File.readPack(): ResourcePack? {
    val reader = MinecraftResourcePackReader.minecraft()
    return when {
        !exists() -> null
        isDirectory && !listFiles().isNullOrEmpty() -> reader.readFromDirectory(this)
        extension == "zip" -> reader.readFromZipFile(this)
        else -> null
    }
}

fun Response.downloadZipFromGithubResponse(template: PackyTemplate) {
    val (owner, repo, _, subPath) = template.githubDownload ?: return logError("${template.id} has no githubDownload, skipping...")
    val zipStream = ZipInputStream(body!!.byteStream())

    runCatching {
        template.path.deleteIfExists()

        ZipOutputStream(FileOutputStream(template.path.toFile())).use { zipOutputStream ->
            var entry = zipStream.nextEntry

            while (entry != null) {
                if ( entry.name.startsWith("$owner-$repo", true) && !entry.isDirectory) {
                    entry.name.substringAfter("/${subPath?.let { "$it/" } ?: ""}").takeIf { it != entry!!.name }?.let { fileName ->
                        zipOutputStream.putNextEntry(ZipEntry(fileName))

                        val buffer = ByteArray(1024)
                        var len: Int
                        while (zipStream.read(buffer).also { len = it } > 0)
                            zipOutputStream.write(buffer, 0, len)

                        zipOutputStream.closeEntry()
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }
    }.onFailure { it.printStackTrace() }.also { zipStream.close() }
}

typealias TemplateIds = SortedSet<String>

fun zipDirectory(directory: File, zipDest: File) {
    val files = directory.walkTopDown().toList()
    val buffer = ByteArray(1024)

    ZipOutputStream(FileOutputStream(zipDest)).use { zipOutputStream ->
        files.forEach { file ->
            val entryName = directory.toPath().relativize(file.toPath()).toString()
            val entry = ZipEntry(entryName)
            zipOutputStream.putNextEntry(entry)

            if (file.isDirectory) return@forEach

            FileInputStream(file).use { inputStream ->
                var len: Int
                while (inputStream.read(buffer).also { len = it } > 0) {
                    zipOutputStream.write(buffer, 0, len)
                }
            }
            zipOutputStream.closeEntry()
        }
    }
}

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
