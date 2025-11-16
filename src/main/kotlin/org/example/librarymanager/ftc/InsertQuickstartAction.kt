package org.example.librarymanager.ftc

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.application.ApplicationManager
import java.net.URL
import java.io.InputStream

class InsertQuickstartAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        // Show only when a directory is available in the view
        val view = e.getData(LangDataKeys.IDE_VIEW)
        val dir = view?.getOrChooseDirectory()
        e.presentation.isEnabledAndVisible = dir != null
        e.presentation.text = "Insert Quickstart Code"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val view = e.getData(LangDataKeys.IDE_VIEW)
        val baseDir = view?.getOrChooseDirectory()
        if (baseDir == null) {
            Messages.showErrorDialog(project, "Please select a target directory.", "No Directory Selected")
            return
        }

        val quickstarts = arrayOf(
            "pedroPathing",
            "RoadRunner"
        )

        val idx = Messages.showChooseDialog(
            "Select which quickstart to insert:",
            "Insert Quickstart",
            quickstarts,
            quickstarts[0],
            null
        )
        if (idx < 0) return
        val choice = quickstarts[idx]

        WriteCommandAction.runWriteCommandAction(project) {
            val targetDir = ensureTargetDirectory(baseDir, choice)
            val ok = copyTemplateFolder(choice, targetDir)
            if (!ok) {
                Messages.showErrorDialog(project,
                    "Templates for \"$choice\" not found under resources/fileTemplates/internal/$choice/",
                    "Quickstart Not Found"
                )
                return@runWriteCommandAction
            }
            // Refresh filesystem to show newly created files
            targetDir.virtualFile.refresh(false, true)
        }

        Messages.showInfoMessage(
            project,
            "Inserted: $choice quickstart",
            "Success"
        )
    }

    /** Ensures <selectedDirectory>/<libraryName>/ exists */
    private fun ensureTargetDirectory(base: PsiDirectory, name: String): PsiDirectory {
        return base.findSubdirectory(name) ?: base.createSubdirectory(name)
    }

    /**
     * Loads template folder from resources:
     * fileTemplates/internal/<name>/
     * and copies all .ft files into the target directory.
     * Returns true if templates were found.
     */
    private fun copyTemplateFolder(templateName: String, targetDir: PsiDirectory): Boolean {
        val basePath = "fileTemplates/internal/$templateName"
        val root = resolveTemplateRoot(basePath) ?: return false
        root.children?.forEach { child ->
            copyRecursive(child, targetDir)
        }
        return true
    }

    /**
     * Resolves a VirtualFile for a resource directory, supporting both dev and JAR runs.
     */
    private fun resolveTemplateRoot(resourceDirPath: String): VirtualFile? {
        val cl = javaClass.classLoader
        val url: URL = cl.getResource("$resourceDirPath/") ?: cl.getResource(resourceDirPath) ?: return null

        // Try direct VFS resolution first
        VfsUtil.findFileByURL(url)?.let { vf ->
            vf.refresh(false, true)
            return vf
        }

        // Fallback for jar: URL
        val asString = url.toString()
        if (asString.startsWith("jar:")) {
            val jarPath = asString.removePrefix("jar:").substringBefore("!")
            val inner = asString.substringAfter("!/")
            val jarFs = StandardFileSystems.jar()
            val vf = jarFs.findFileByPath("$jarPath!/$inner")
            vf?.refresh(false, true)
            return vf
        }

        return null
    }

    /**
     * Recursively copies templates, preserving folder structure.
     */
    private fun copyRecursive(source: VirtualFile, targetDir: PsiDirectory) {
        val psiManager = PsiManager.getInstance(targetDir.project)

        if (source.isDirectory) {
            val newDir = targetDir.findSubdirectory(source.name)
                ?: targetDir.createSubdirectory(source.name)

            // Ensure directory listing is up-to-date
            source.refresh(false, true)
            source.children?.forEach { child ->
                copyRecursive(child, newDir)
            }
        } else if (source.name.endsWith(".ft")) {
            val filename = source.name.removeSuffix(".ft")
            val psiFile = targetDir.findFile("$filename")
                ?: targetDir.createFile(filename)

            // Read template, expand variables, and write to file (under write action already)
            source.inputStream.use { input: InputStream ->
                val text = input.readBytes().decodeToString()
                // Compute package using source root → relative path → dotted package
                val fileIndex = com.intellij.openapi.roots.ProjectFileIndex.getInstance(targetDir.project)
                val sourceRoot = fileIndex.getSourceRootForFile(targetDir.virtualFile)

                val packageName =
                    if (sourceRoot != null) {
                        val rel = VfsUtil.getRelativePath(targetDir.virtualFile, sourceRoot, '/')
                        val computed = (rel ?: "").replace('/', '.')
                        if (computed.isBlank()) {
                            // fallback to parent directory package
                            targetDir.parentDirectory?.let { parent ->
                                val relParent = VfsUtil.getRelativePath(parent.virtualFile, sourceRoot, '/')
                                (relParent ?: "").replace('/', '.')
                            } ?: ""
                        } else {
                            computed
                        }
                    } else {
                        // absolute fallback if source root couldn't be detected (sandbox mode)
                        "org.firstinspires.ftc.teamcode"
                    }

                val expanded = text.replace("\${PACKAGE_NAME}", packageName)
                    .replace("package \${PackageName};", "package \${PACKAGE_NAME};")
                psiFile.virtualFile.setBinaryContent(expanded.toByteArray())
                psiFile.virtualFile.refresh(false, false)
            }
        }
    }
}