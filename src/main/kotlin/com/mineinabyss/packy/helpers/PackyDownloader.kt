package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.google.gson.JsonParser
import com.mineinabyss.idofront.messaging.*
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.PackyServer.cachedPacks
import com.mineinabyss.packy.helpers.PackyServer.cachedPacksByteArray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.StandardOpenOption
import kotlin.io.path.*

object PackyDownloader {

    fun updateGithubTemplate(template: PackyTemplate): Boolean {
        val hashFile = packy.plugin.dataFolder.toPath() / "templates" / "localHashes.txt"
        hashFile.createParentDirectories()
        hashFile.toFile().createNewFile()

        val latestHash = latestCommitHash(template.githubDownload ?: return false) ?: return false
        val localHash = hashFile.readLines().find { it.matches("${template.id}=.*".toRegex()) }?.substringAfter("=")

        when {
            localHash == null || localHash != latestHash -> {
                downloadAndExtractGithub(template)
                val lines = hashFile.readLines().toMutableSet().apply { removeIf { it.startsWith(template.id) } }
                lines += "${template.id}=$latestHash"
                hashFile.writeLines(lines)
                logInfo("Updated hash for ${template.id}")
            }

            else -> logSuccess("Skipping download for ${template.id}, no changes applied to remote")
        }
        return localHash == null || localHash != latestHash
    }

    private fun latestCommitHash(githubDownload: PackyTemplate.GithubDownload): String? {
        val url = githubDownload.let { "https://api.github.com/repos/${it.org}/${it.repo}/git/trees/${it.branch}" }

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + packy.accessToken.token)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use logWarn("Failed to get latest hash")
            val reader = response.body!!.charStream()
            return JsonParser.parseReader(reader)?.asJsonObject?.get("sha")?.asString
                ?: return@use logWarn("Failed to read response")
        }

        return null
    }

    fun downloadTemplates() {
        packy.templates.entries.filter { it.value.githubDownload != null }.map { (id, template) ->
            packy.plugin.launch(packy.plugin.asyncDispatcher) {
                logWarn("Downloading ${id}-template from GitHub...")
                if (updateGithubTemplate(template)) {
                    logSuccess("Successfully downloaded ${id}-template!")
                    cachedPacks.keys.removeIf { id in it }
                    cachedPacksByteArray.keys.removeIf { id in it }
                }
            }
        }
    }

    fun downloadAndExtractGithub(template: PackyTemplate) {
        val (owner, repo, branch, _) = template.githubDownload ?: return logError("${template.id} has no githubDownload, skipping...")
        val url = "https://api.github.com/repos/${owner}/${repo}/zipball/${branch}"


        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + packy.accessToken.token)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use logError("Failed to download template ${template.id} via $url")
            response.downloadZipFromGithubResponse(template)
        }
    }
}
