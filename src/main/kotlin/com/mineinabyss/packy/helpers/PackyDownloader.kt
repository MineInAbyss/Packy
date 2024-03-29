package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.google.gson.JsonParser
import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.messaging.logInfo
import com.mineinabyss.idofront.messaging.logSuccess
import com.mineinabyss.idofront.messaging.logWarn
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.packy
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.io.path.*

object PackyDownloader {
    var startupJob: Job? = null

    fun updateGithubTemplate(template: PackyTemplate): Boolean {
        val hashFile = packy.plugin.dataFolder.toPath() / "templates" / "localHashes.txt"
        hashFile.createParentDirectories()
        hashFile.toFile().createNewFile()

        val templateExists = template.path.exists()
        val latestHash = latestCommitHash(template.githubDownload ?: return false) ?: return false
        val localHash = hashFile.readLines().find { it.matches("${template.id}=.*".toRegex()) }?.substringAfter("=")

        when {
            !templateExists || localHash == null || localHash != latestHash -> {
                downloadAndExtractGithub(template)
                val lines = hashFile.readLines().toMutableSet().apply { removeIf { it.startsWith(template.id) } }
                lines += "${template.id}=$latestHash"
                hashFile.writeLines(lines)
                if (templateExists) logSuccess("Updated hash for ${template.id}")
            }

            else -> logSuccess("Skipping download for ${template.id}, no changes applied to remote")
        }
        return !templateExists || localHash == null || localHash != latestHash
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
        startupJob = packy.plugin.launch(packy.plugin.asyncDispatcher) {
            packy.templates.entries.filter { it.value.githubDownload != null }.map { (id, template) ->
                launch {
                    logWarn("Downloading ${id}-template from GitHub...")
                    if (updateGithubTemplate(template)) {
                        logSuccess("Successfully downloaded ${id}-template!")
                        PackyGenerator.cachedPacks.keys.removeIf { id in it }
                        PackyGenerator.cachedPacksByteArray.keys.removeIf { id in it }
                    }
                    if (packy.config.packSquash.enabled) {
                        logInfo("Starting PackSquash process for $id-template...")
                        PackySquash.squashPackyTemplate(template)
                        logSuccess("Finished PackSquash process for $id-template")
                    }
                }
            }
        }
    }

    fun downloadAndExtractGithub(template: PackyTemplate) {
        val (owner, repo, branch, _) = template.githubDownload
            ?: return logError("${template.id} has no githubDownload, skipping...")
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
