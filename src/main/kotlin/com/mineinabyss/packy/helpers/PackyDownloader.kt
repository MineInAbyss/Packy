package com.mineinabyss.packy.helpers

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.google.gson.JsonParser
import com.mineinabyss.idofront.textcomponents.miniMsg
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
                if (templateExists) packy.logger.iSuccess("Updated hash for ${template.id}")
            }

            else -> packy.logger.iSuccess("Template up to date: <dark_gray>${template.id}".miniMsg())
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
            if (!response.isSuccessful) return@use packy.logger.w("Failed to get latest hash")
            val reader = response.body!!.charStream()
            return JsonParser.parseReader(reader)?.asJsonObject?.get("sha")?.asString
                ?: return@use packy.logger.w("Failed to read response")
        }

        return null
    }

    fun downloadTemplates() {
        startupJob = packy.plugin.launch(packy.plugin.asyncDispatcher) {
            packy.templates.entries.filter { it.value.githubDownload != null }.map { (id, template) ->
                launch {
                    packy.logger.i("Checking updates (GitHub): <dark_gray>$id".miniMsg())
                    if (updateGithubTemplate(template)) {
                        packy.logger.iSuccess("Successfully downloaded ${id}-template!")
                        PackyGenerator.cachedPacks.keys.removeIf { id in it }
                        PackyGenerator.cachedPacksByteArray.keys.removeIf { id in it }
                    }
                    if (packy.config.packSquash.enabled) {
                        packy.logger.i("Starting PackSquash process for $id-template...")
                        PackySquash.squashPackyTemplate(template)
                        packy.logger.iSuccess("Finished PackSquash process for $id-template")
                    }
                }
            }
        }
    }

    fun downloadAndExtractGithub(template: PackyTemplate) {
        val (owner, repo, branch, _) = template.githubDownload
            ?: return packy.logger.e("${template.id} has no githubDownload, skipping...")
        val url = "https://api.github.com/repos/${owner}/${repo}/zipball/${branch}"


        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + packy.accessToken.token)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use packy.logger.e("Failed to download template ${template.id} via $url")
            response.downloadZipFromGithubResponse(template)
        }
    }
}
