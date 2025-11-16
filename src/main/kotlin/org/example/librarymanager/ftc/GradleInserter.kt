package org.example.librarymanager.ftc

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

object GradleInserter {

    private fun findDependenciesFile(project: Project): VirtualFile? {
        val base = project.basePath ?: return null
        val paths = listOf(
            "$base/build.dependencies.gradle",
            "$base/TeamCode/build.dependencies.gradle"
        )
        for (p in paths) {
            val f = LocalFileSystem.getInstance().findFileByPath(p)
            if (f != null && f.exists()) return f
        }
        return null
    }

    private fun findBuildGradleFile(project: Project): VirtualFile? {
        val base = project.basePath ?: return null
        val paths = listOf(
            "$base/build.gradle",
            "$base/TeamCode/build.gradle"
        )
        for (p in paths) {
            val f = LocalFileSystem.getInstance().findFileByPath(p)
            if (f != null && f.exists()) return f
        }
        return null
    }

    fun insertDependency(project: Project, dependency: String, repoUrls: List<String> = emptyList()) {
        if (repoUrls.isNotEmpty()) {
            ensureRepositories(project, repoUrls)
        }

        val file = findDependenciesFile(project) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = FileDocumentManager.getInstance().getDocument(file) ?: return@runWriteCommandAction
            val prefix = dependency.substringBeforeLast(":")
            val lines = doc.text.lines()
                .filterNot { it.contains("implementation") && it.contains(prefix + ":") }
            val cleaned = lines.joinToString("\n")
            val newText =
                if (!cleaned.contains("dependencies {"))
                    cleaned + "\n\ndependencies {\n    implementation \"$dependency\"\n}\n"
                else {
                    val idx = cleaned.indexOf("{", cleaned.indexOf("dependencies {")) + 1
                    cleaned.substring(0, idx) +
                        "\n    implementation \"$dependency\"" +
                        cleaned.substring(idx)
                }
            doc.setText(newText)
            FileDocumentManager.getInstance().saveDocument(doc)
        }
    }

    private fun ensureRepositories(project: Project, repoUrls: List<String>) {
        val file = findDependenciesFile(project) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = FileDocumentManager.getInstance().getDocument(file) ?: return@runWriteCommandAction
            var content = doc.text

            // Always ensure both Dairy repos if any Dairy URL is requested
            val needsDairy = repoUrls.any { it.contains("dairy.foundation") }
            val dairyReleases = "https://repo.dairy.foundation/releases"
            val dairySnapshots = "https://repo.dairy.foundation/snapshots"
            val allNeeded = mutableSetOf<String>().apply {
                addAll(repoUrls)
                if (needsDairy) {
                    add(dairyReleases)
                    add(dairySnapshots)
                }
            }

            val repoBlockPattern = Regex("repositories\\s*\\{")
            val match = repoBlockPattern.find(content)

            if (match != null) {
                val insertPos = match.range.last + 1
                val missing = allNeeded.filterNot { content.contains(it) }
                if (missing.isNotEmpty()) {
                    val toAdd = missing.joinToString("") { url ->
                        val entry = if (url.contains("jitpack.io"))
                            "\n\tmaven { url 'https://jitpack.io' }"
                        else
                            "\n\tmaven { url \"$url\" }"
                        entry
                    }
                    content = content.substring(0, insertPos) + toAdd + content.substring(insertPos)
                    doc.setText(content)
                    FileDocumentManager.getInstance().saveDocument(doc)
                    println("Added repositories: $missing")
                } else {
                    println("All repositories already present")
                }
            } else {
                // Create repositories block
                val block = buildString {
                    append("\n\nrepositories {")
                    allNeeded.forEach { url ->
                        if (url.contains("jitpack.io"))
                            append("\n\tmaven { url 'https://jitpack.io' }")
                        else
                            append("\n\tmaven { url \"$url\" }")
                    }
                    append("\n}\n")
                }
                doc.setText(content + block)
                FileDocumentManager.getInstance().saveDocument(doc)
                println("Created repositories block with: $allNeeded")
            }
        }
    }

    fun deleteDependency(project: Project, prefix: String) {
        val file = findDependenciesFile(project) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = FileDocumentManager.getInstance().getDocument(file) ?: return@runWriteCommandAction
            val cleaned = doc.text.lines()
                .filterNot { it.contains("implementation") && it.contains(prefix + ":") }
                .joinToString("\n")
            doc.setText(cleaned)
            FileDocumentManager.getInstance().saveDocument(doc)
        }
        // After dependency removal, clean up repositories no longer needed
        cleanupRepositories(project)
    }

    private fun cleanupRepositories(project: Project) {
        val file = findDependenciesFile(project) ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = FileDocumentManager.getInstance().getDocument(file) ?: return@runWriteCommandAction
            val contentBefore = doc.text

            val installed = getInstalledDependencies(project)
            val installedPrefixes = installed.map { it.substringBeforeLast(":") }
            val sourcesByPrefix = LibraryRegistry.LIBRARIES.values.associateBy { "${it.group}:${it.artifact}" }

            val requiredRepos = mutableSetOf<String>()
            installedPrefixes.forEach { p ->
                sourcesByPrefix[p]?.repositories?.forEach { requiredRepos.add(it) }
            }

            val dairyInUse = requiredRepos.any { it.contains("dairy.foundation") }
            if (dairyInUse) {
                requiredRepos.add("https://repo.dairy.foundation/releases")
                requiredRepos.add("https://repo.dairy.foundation/snapshots")
            }

            val managedRepos = setOf(
                "https://jitpack.io",
                "https://repo.dairy.foundation/releases",
                "https://repo.dairy.foundation/snapshots"
            )

            val startIdx = contentBefore.indexOf("repositories {")
            if (startIdx == -1) {
                // Nothing to clean
                return@runWriteCommandAction
            }
            // Find matching closing brace (simple scan)
            var braceDepth = 0
            var endIdx = -1
            for (i in startIdx until contentBefore.length) {
                val c = contentBefore[i]
                if (c == '{') braceDepth++
                if (c == '}') {
                    braceDepth--
                    if (braceDepth == 0) {
                        endIdx = i
                        break
                    }
                }
            }
            if (endIdx == -1) return@runWriteCommandAction

            val block = contentBefore.substring(startIdx, endIdx + 1)
            val lines = block.lines()

            // Extract non-managed lines and required managed lines
            val newRepoLines = mutableListOf<String>()
            for (line in lines) {
                val urlMatch = Regex("""url\s+['"]([^'"]+)['"]""").find(line)
                val url = urlMatch?.groupValues?.getOrNull(1)
                if (url == null) {
                    // Keep structural or other repo lines (e.g., mavenCentral())
                    if (!line.trim().startsWith("maven {") || line.contains("url")) {
                        newRepoLines.add(line)
                    } else {
                        // keep non-managed generic entries like mavenCentral(), google(), etc.
                        if (!line.contains("url")) newRepoLines.add(line)
                    }
                } else {
                    if (url !in managedRepos) {
                        newRepoLines.add(line) // custom user repo, keep
                    } else if (url in requiredRepos) {
                        newRepoLines.add(line) // still needed
                    }
                }
            }

            // Ensure required managed repos exist if needed but missing
            val existingManaged = newRepoLines.joinToString("\n")
            managedRepos.forEach { m ->
                if (m in requiredRepos && !existingManaged.contains(m)) {
                    val addLine = if (m.contains("jitpack.io"))
                        "\tmaven { url 'https://jitpack.io' }"
                    else
                        "\tmaven { url \"$m\" }"
                    // Insert before closing brace
                    val closingIndex = newRepoLines.indexOfLast { it.contains("}") }
                    if (closingIndex != -1) {
                        newRepoLines.add(closingIndex, addLine)
                    } else {
                        newRepoLines.add(addLine)
                    }
                }
            }

            // If after cleaning only braces remain and no repos required, remove entire block
            val nonBraceContent = newRepoLines.filter { it.trim().isNotEmpty() && it.trim() != "repositories {" && it.trim() != "}" }
            val contentAfter = if (nonBraceContent.isEmpty() && requiredRepos.isEmpty()) {
                // Remove whole block
                contentBefore.removeRange(startIdx, endIdx + 1)
            } else {
                // Rebuild block
                val rebuilt = newRepoLines.joinToString("\n")
                contentBefore.substring(0, startIdx) + rebuilt + contentBefore.substring(endIdx + 1)
            }

            if (contentAfter != contentBefore) {
                doc.setText(contentAfter)
                FileDocumentManager.getInstance().saveDocument(doc)
                println("Repository block cleaned. Required repos now: $requiredRepos")
            } else {
                println("No changes to repositories block required.")
            }
        }
    }

    fun getInstalledDependencies(project: Project): List<String> {
        val file = findDependenciesFile(project) ?: return emptyList()
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()

        val implementationRegex = Regex("""implementation\s+["']([^"']+)["']""")
        return doc.text.lines()
            .mapNotNull { line ->
                implementationRegex.find(line)?.groupValues?.getOrNull(1)
            }
            .filter { it.isNotBlank() }
    }
}