package org.example.librarymanager.ftc

import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

object VersionFetcher {
    // Simple cache to avoid repeated network fetching in same run.
    private val cache = ConcurrentHashMap<String, List<String>>()

    fun fetchVersions(src: LibrarySource): List<String> {
        val key = "${src.group}:${src.artifact}@${src.repoUrl}"
        cache[key]?.let { return it }

        // Remove special static handling for Sloth Dash - now uses Dairy Foundation

        // UPDATED: Panels (FullPanels) suite now uses group com.bylazar
        if (src.group == "com.bylazar" &&
            (src.repoUrl.contains("mymaven.bylazar.com") || src.repoUrl.contains("panels.bylazar.com"))
        ) {
            val v = fetchPanelsVersions(src)
            cache[key] = v
            return v
        }

        return try {
            val result = when {
                // JITPACK → use API
                src.repoUrl.contains("jitpack.io") ->
                    fetchJitpackVersions(src)

                // DAIRY FOUNDATION → use HTML scraping (now includes SolversLib and Sloth Dash)
                src.repoUrl.contains("dairy.foundation") ->
                    fetchDairyVersions(src)

                // NEXTFTC (both dev.nextftc and dev.nextftc.extensions) or Maven Central → use standard metadata
                src.group.startsWith("dev.nextftc") || src.repoUrl.contains("maven.org") ->
                    fetchMavenMetadata(src)

                // GOOGLE MAVEN → must use HTML scraping
                src.repoUrl.contains("google.com") ->
                    fetchGoogleMavenVersions(src)

                // Standard Maven repos → use metadata
                else -> {
                    println("Using Maven metadata for ${src.group}:${src.artifact} at ${src.repoUrl}")
                    fetchMavenMetadata(src)
                }
            }
            cache[key] = result
            result
        } catch (e: Exception) {
            println("VersionFetcher ERROR for $key → ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    // ----------------------------------------------------
    // 1. JITPACK API (FTCLib, Road Runner, GitHub libs)
    // ----------------------------------------------------
    private fun fetchJitpackVersions(src: LibrarySource): List<String> {
        return try {
            // Try multiple approaches for JitPack

            // Approach 1: Try GitHub releases API first
            val githubVersions = tryGitHubReleases(src)
            if (githubVersions.isNotEmpty()) {
                println("Got ${githubVersions.size} versions from GitHub API")
                return githubVersions.sortedWith(compareVersion())
            }

            // Approach 2: Try JitPack maven-metadata.xml
            val mavenVersions = tryJitPackMavenMetadata(src)
            if (mavenVersions.isNotEmpty()) {
                println("Got ${mavenVersions.size} versions from JitPack maven-metadata")
                return mavenVersions.sortedWith(compareVersion())
            }

            // Approach 3: Try JitPack API as fallback
            val parts = src.group.removePrefix("com.github.").split(".")
            val userRepo = if (parts.size >= 2) {
                "${parts[0]}/${parts[1]}"
            } else {
                parts[0]
            }

            val apiUrl = "https://jitpack.io/api/builds/$userRepo"
            println("Trying JitPack API: $apiUrl")

            val text = URL(apiUrl).readText()
            println("JitPack API response length: ${text.length}")

            // Parse JSON response
            val versionRegex = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
            val versions = versionRegex.findAll(text)
                .map { it.groupValues[1] }
                .filter { it.isNotBlank() && !it.contains("-SNAPSHOT") && it.matches(Regex("v?\\d+.*")) }
                .distinct()
                .sortedWith(compareVersion())
                .take(20)
                .toList()

            println("Found ${versions.size} versions from JitPack API")
            versions
        } catch (e: Exception) {
            println("All JitPack methods failed for ${src.group}:${src.artifact}")
            e.printStackTrace()
            emptyList()
        }
    }

    private fun tryGitHubReleases(src: LibrarySource): List<String> {
        return try {
            // Handle both com.github.X and com.pedropathing formats
            val parts = src.group.removePrefix("com.github.").removePrefix("com.").split(".")
            val user = when {
                src.group.startsWith("com.pedropathing") -> "Pedro-Pathing"
                parts.size >= 2 -> parts[0]
                else -> parts[0]
            }
            val repo = when {
                src.group.startsWith("com.pedropathing") -> "PedroPathing"
                parts.size >= 2 -> parts[1]
                else -> parts[0]
            }

            val apiUrl = "https://api.github.com/repos/$user/$repo/releases"
            println("Trying GitHub Releases API: $apiUrl")

            val connection = URL(apiUrl).openConnection()
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val text = connection.getInputStream().bufferedReader().readText()

            val tagRegex = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
            val versions = tagRegex.findAll(text)
                .map { it.groupValues[1].removePrefix("v") }
                .filter { it.matches(Regex("\\d+.*")) }
                .distinct()
                .take(20)
                .toList()

            println("GitHub API found ${versions.size} versions")
            versions
        } catch (e: Exception) {
            println("GitHub API failed: ${e.message}")
            emptyList()
        }
    }

    private fun tryJitPackMavenMetadata(src: LibrarySource): List<String> {
        return try {
            // Convert com.pedropathing to proper JitPack path
            val groupPath = when {
                src.group.startsWith("com.pedropathing") -> "com/github/Pedro-Pathing/PedroPathing"
                else -> src.group.replace('.', '/')
            }
            val url = "https://jitpack.io/$groupPath/${src.artifact}/maven-metadata.xml"
            println("Trying JitPack maven-metadata: $url")

            val conn = URL(url).openConnection().apply {
                connectTimeout = 5000
                readTimeout = 5000
            }

            val builder = secureDocumentBuilder()
            val doc = builder.parse(conn.getInputStream())
            val nodes = doc.getElementsByTagName("version")

            val list = mutableListOf<String>()
            for (i in 0 until nodes.length) {
                val v = nodes.item(i).textContent.trim()
                if (v.isNotBlank() && !v.contains("-SNAPSHOT")) {
                    list.add(v)
                }
            }

            println("Maven metadata found ${list.size} versions")
            list.take(20)
        } catch (e: Exception) {
            println("Maven metadata failed: ${e.message}")
            emptyList()
        }
    }

    // ----------------------------------------------------
    // 2. GOOGLE MAVEN (FTC SDK artifacts)
    // ----------------------------------------------------
    private fun fetchGoogleMavenVersions(src: LibrarySource): List<String> {
        return try {
            val base = src.repoUrl.removeSuffix("/")
            val url = "$base/${src.group.replace('.', '/')}/${src.artifact}/"

            val html = URL(url).readText()

            // Look for directory names like "11.0.0/"
            val regex = Regex("<a href=\"([0-9A-Za-z._\\-]+)/\">")
            val versions = regex.findAll(html)
                .map { it.groupValues[1] }
                .filter { it.matches(Regex("[0-9].*")) }
                .sortedWith(compareVersion())
                .toList()

            versions
        } catch (e: Exception) {
            println("Google Maven fetch failed for ${src.group}:${src.artifact} → ${e.message}")
            emptyList()
        }
    }

    // ----------------------------------------------------
    // 3. MAVEN METADATA XML
    // ----------------------------------------------------
    private fun fetchMavenMetadata(src: LibrarySource): List<String> {
        return try {
            val groupPath = src.group.replace('.', '/')
            val url = "${src.repoUrl.removeSuffix("/")}/$groupPath/${src.artifact}/maven-metadata.xml"

            println("Fetching Maven metadata from: $url")

            val conn = URL(url).openConnection().apply {
                connectTimeout = 5000
                readTimeout = 5000
            }

            val builder = secureDocumentBuilder()
            val inputStream = conn.getInputStream()
            val doc = builder.parse(inputStream)

            // Try both "version" and "versions/version" tags
            val versionNodes = doc.getElementsByTagName("version")

            println("Found ${versionNodes.length} version nodes in metadata")

            val list = mutableListOf<String>()
            for (i in 0 until versionNodes.length) {
                val v = versionNodes.item(i).textContent.trim()
                println("  Version found: $v")
                if (v.isNotBlank() && !v.contains("-SNAPSHOT")) {
                    list.add(v)
                }
            }

            val sorted = list.sortedWith(compareVersion())
            println("Returning ${sorted.size} versions for ${src.group}:${src.artifact}")
            sorted
        } catch (e: Exception) {
            println("Metadata fetch failed for ${src.group}:${src.artifact}")
            println("  Error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    // ----------------------------------------------------
    // 4. DAIRY FOUNDATION (Custom repo)
    // ----------------------------------------------------
    private fun fetchDairyVersions(src: LibrarySource): List<String> {
        return try {
            // Dairy Foundation structure: https://repo.dairy.foundation/releases/{groupPath}/{artifact}/
            // Examples:
            // - Core: dev/frozenmilk/dairy/Core
            // - Mercurial: dev/frozenmilk/mercurial/Mercurial
            val groupPath = src.group.replace('.', '/')
            val releasesUrl = "https://repo.dairy.foundation/releases/$groupPath/${src.artifact}/"

            println("Attempting to scrape Dairy releases: $releasesUrl")

            try {
                val conn = URL(releasesUrl).openConnection().apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }

                val html = conn.getInputStream().bufferedReader().readText()

                // Look for version directories in HTML
                val versionPatterns = listOf(
                    Regex("""<a href="([0-9]+\.[0-9]+\.[0-9]+[^"]*)/""""),
                    Regex("""href="([0-9]+\.[0-9]+\.[0-9]+[^"]*)/""""),
                    Regex(""">([0-9]+\.[0-9]+\.[0-9]+[^<]*)/</a>""")
                )

                val versions = mutableSetOf<String>()
                for (pattern in versionPatterns) {
                    pattern.findAll(html).forEach { match ->
                        val version = match.groupValues[1].removeSuffix("/").trim()
                        if (version.matches(Regex("\\d+\\.\\d+\\.\\d+.*")) &&
                            !version.contains("SNAPSHOT", ignoreCase = true) &&
                            !version.contains("maven-metadata")) {
                            versions.add(version)
                        }
                    }
                }

                if (versions.isNotEmpty()) {
                    val sorted = versions.toList().sortedWith(compareVersion())
                    println("Scraped ${sorted.size} versions from Dairy for ${src.artifact}: $sorted")
                    return sorted
                } else {
                    println("No versions found in HTML, trying maven-metadata.xml")
                    // Try maven-metadata.xml as fallback
                    val metadataUrl = "${releasesUrl}maven-metadata.xml"
                    try {
                        val metaConn = URL(metadataUrl).openConnection().apply {
                            connectTimeout = 5000
                            readTimeout = 5000
                        }
                        val builder = secureDocumentBuilder()
                        val doc = builder.parse(metaConn.getInputStream())
                        val nodes = doc.getElementsByTagName("version")

                        val metaVersions = mutableListOf<String>()
                        for (i in 0 until nodes.length) {
                            val v = nodes.item(i).textContent.trim()
                            if (v.isNotBlank() && !v.contains("SNAPSHOT")) {
                                metaVersions.add(v)
                            }
                        }

                        if (metaVersions.isNotEmpty()) {
                            println("Found ${metaVersions.size} versions from maven-metadata.xml")
                            return metaVersions.sortedWith(compareVersion())
                        }
                    } catch (e: Exception) {
                        println("Maven metadata also failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("HTML scraping failed for $releasesUrl: ${e.message}")
            }

            // Fallback to known versions
            println("Using fallback versions for Dairy ${src.artifact}")
            getDairyKnownVersions(src.artifact)
        } catch (e: Exception) {
            println("All Dairy fetch methods failed for ${src.group}:${src.artifact}: ${e.message}")
            getDairyKnownVersions(src.artifact)
        }
    }

    private fun scrapeDairyDirectoryVersions(src: LibrarySource): List<String> {
        // This method is now redundant with fetchDairyVersions, but kept for compatibility
        return emptyList()
    }

    // New unified Panels plugin version fetch
    private fun fetchPanelsVersions(src: LibrarySource): List<String> {
        // 1. Try Maven metadata first
        val meta = fetchMavenMetadata(src)
        if (meta.isNotEmpty()) {
            println("Panels metadata versions for ${src.artifact}: $meta")
            return meta
        }

        // 2. Try HTML directory listing (releases + snapshots)
        val html = scrapePanelsVersionDirs(src)
        if (html.isNotEmpty()) {
            println("Panels HTML versions for ${src.artifact}: $html")
            return html.sortedWith(compareVersion())
        }

        // 3. Fallback heuristics if nothing found
        val fallback = when (src.artifact.lowercase()) {
            "fullpanels", "panels" -> listOf("0.1.0", "0.1.1")
            "telemetry" -> listOf("0.1.0", "0.1.1")
            else -> listOf("0.1.0")
        }
        println("Panels fallback for ${src.artifact}: $fallback")
        return fallback
    }

    private fun scrapePanelsVersionDirs(src: LibrarySource): List<String> {
        val groupPath = src.group.replace('.', '/')
        val bases = listOf(
            "${src.repoUrl.removeSuffix("/")}/$groupPath/${src.artifact}/",
            src.repoUrl.replace("releases", "snapshots").removeSuffix("/") + "/$groupPath/${src.artifact}/"
        )
        val dirRegex = Regex("<a href=\"([0-9A-Za-z._\\-]+)/\">")
        val versions = mutableSetOf<String>()
        bases.forEach { base ->
            try {
                val html = URL(base).openStream().bufferedReader().readText()
                dirRegex.findAll(html)
                    .map { it.groupValues[1].removeSuffix("/") }
                    .filter { it.matches(Regex("\\d+.*")) && !it.contains("SNAPSHOT") }
                    .forEach { versions += it }
            } catch (_: Exception) { }
        }
        return versions.toList()
    }

    // Fallback to known versions if API is not accessible
    private fun getDairyKnownVersions(artifact: String): List<String> {
        val normalized = when (artifact.lowercase()) {
            "pasetrized" -> "Pasteurized"
            "pedropathing" -> "pedroPathing"
            "dashboard" -> "dashboard"  // Sloth Dash
            else -> artifact
        }
        return when (normalized) {
            "Core" -> listOf("1.0.0", "1.1.0", "1.2.0", "1.3.0", "1.4.0", "1.5.0", "1.6.0")
            "Mercurial" -> listOf("1.0.0", "1.1.0", "1.2.0", "1.3.0", "1.4.0")
            "Pasteurized" -> listOf("1.0.0", "1.1.0", "1.2.0", "1.3.0", "1.4.0")
            "Sinister" -> listOf("1.0.0", "1.1.0", "1.2.0", "1.3.0")
            "Sloth" -> listOf("1.0.0", "1.1.0", "1.2.0", "1.3.0")
            "Util" -> listOf("1.0.0", "1.1.0", "1.2.0")
            "dashboard" -> listOf("0.2.4+0.4.17", "0.2.5+0.4.17")  // Sloth Dash
            "core" -> listOf("1.0.0", "1.1.0", "1.2.0") // SolversLib core
            "pedroPathing" -> listOf("1.0.0", "1.1.0") // SolversLib Pedro Pathing integration
            else -> listOf("1.0.0")
        }.also {
            println("Using known versions for Dairy $normalized: $it")
        }
    }

    // Comparator for semantic version sorting
    private fun compareVersion(): Comparator<String> {
        return Comparator { v1, v2 ->
            val parts1 = v1.removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }
            val parts2 = v2.removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }

            for (i in 0 until maxOf(parts1.size, parts2.size)) {
                val p1 = parts1.getOrNull(i) ?: 0
                val p2 = parts2.getOrNull(i) ?: 0
                if (p1 != p2) return@Comparator p1.compareTo(p2)
            }
            0
        }
    }

    // Secure builder to avoid XML external entity issues.
    private fun secureDocumentBuilder() =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (e: Exception) {
                // Feature not supported, continue
            }
            try {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            } catch (e: Exception) {
                // Feature not supported, continue
            }
            try {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (e: Exception) {
                // Feature not supported, continue
            }
            try {
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            } catch (e: Exception) {
                // Feature not supported, continue
            }
            isXIncludeAware = false
            isExpandEntityReferences = false
        }.newDocumentBuilder()
}