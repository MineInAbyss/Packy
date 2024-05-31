package com.mineinabyss.packy

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.google.gson.JsonParser
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.packy.config.PackyTemplate
import com.mineinabyss.packy.config.PackyTemplates
import com.mineinabyss.packy.config.packy
import com.mineinabyss.packy.helpers.downloadZipFromGithubResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.io.path.*

object PackyDownloader {
    var startupJob: Job? = null

    fun updateGithubTemplate(vararg templates: PackyTemplate): Boolean {
        val template = templates.first()
        val templateIds = templates.joinToString("|") { it.id }
        val regex = "$templateIds=.*".toRegex()
        val hashFile = packy.plugin.dataFolder.toPath() / "templates" / "localHashes.txt"
        hashFile.createParentDirectories()
        hashFile.toFile().createNewFile()

        val templatesExists = templates.all { it.path.exists() }
        val latestHash = latestCommitHash(template.githubDownload ?: return false) ?: return false
        val localHash = hashFile.readLines().find { it.matches(regex) }?.substringAfter("=")

        when {
            !templatesExists || localHash == null || localHash != latestHash -> {
                downloadAndExtractGithub(*templates)
                val lines = hashFile.readLines().toMutableSet().apply { removeIf { it.matches(regex) } }
                lines += "$templateIds=$latestHash"
                hashFile.writeLines(lines)
                if (templatesExists) packy.logger.s("Updated hash for $templateIds")
            }

            else -> packy.logger.s("Template up to date: <dark_gray>$templateIds".miniMsg())
        }
        return !templatesExists || localHash == null || localHash != latestHash
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
            packy.templates.values.filter { it.githubDownload != null }
                .sortedBy { it.id }
                .groupBy { it.githubDownload!!.key() }
                .map { (_, templates) ->
                    val templateIds = templates.joinToString { it.id }
                    val suffix = "template" + if (templates.size > 1) "s" else ""
                    launch {
                        packy.logger.i("Checking updates (GitHub): <dark_gray>$templateIds".miniMsg())
                        if (updateGithubTemplate(*templates.toTypedArray())) {
                            packy.logger.s("Successfully downloaded ${templateIds}-$suffix!")
                            templates.forEach { template ->
                                PackyGenerator.cachedPacks.keys.removeIf { template.id in it }
                                PackyGenerator.cachedPacksByteArray.keys.removeIf { template.id in it }
                            }
                        }
                        if (packy.config.packSquash.enabled) {
                            packy.logger.i("Starting PackSquash process for $templateIds-$suffix...")
                            templates.forEach(PackySquash::squashPackyTemplate)
                            packy.logger.s("Finished PackSquash process for $templateIds-$suffix")
                        }
                    }
            }
        }
    }

    fun downloadAndExtractGithub(vararg templates: PackyTemplate) {
        val firstTemplate = templates.firstOrNull() ?: return
        val templateIds = templates.joinToString { it.id }
        val (owner, repo, branch, _) = firstTemplate.githubDownload
            ?: return packy.logger.e("$templateIds has no githubDownload, skipping...")
        val url = "https://api.github.com/repos/${owner}/${repo}/zipball/${branch}"


        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/vnd.github+json")
            .header("Authorization", "token " + packy.accessToken.token)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use packy.logger.e("Failed to download template $templateIds via $url")
            response.downloadZipFromGithubResponse(*templates)
        }
    }
}
