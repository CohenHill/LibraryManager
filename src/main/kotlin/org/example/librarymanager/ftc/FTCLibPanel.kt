package org.example.librarymanager.ftc

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import java.awt.*
import java.net.URI
import javax.swing.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentListener
import javax.swing.event.HyperlinkEvent

class FTCLibPanel(private val project: Project) : JPanel() {

    // CHANGED: use GridBagLayout for mainPanel to eliminate left/right cutoff
    private val mainPanel = JPanel(GridBagLayout())
    private var searchQuery = ""

    // Modern color scheme
    private val primaryColor = JBColor(Color(0x007ACC), Color(0x0098FF))
    private val successColor = JBColor(Color(0x28A745), Color(0x2EA043))
    private val dangerColor = JBColor(Color(0xDC3545), Color(0xF85149))
    private val warningColor = JBColor(Color(0xFFC107), Color(0xD29922))
    private val cardBackground = JBColor(Color(0xF5F5F5), Color(0x2B2B2B))
    private val borderColor = JBColor(Color(0xE0E0E0), Color(0x3C3C3C))

    // Simplified: only keep a plain searchField
    private val searchField = JTextField()

    init {
        layout = BorderLayout()
        background = JBColor.background()
        // FIX: reduce horizontal border to prevent right cutoff
        border = EmptyBorder(16, 8, 16, 8)

        // Modern header with search bar
        val headerPanel = JPanel(BorderLayout())
        headerPanel.background = JBColor.background()
        headerPanel.border = EmptyBorder(0, 0, 16, 0)

        // Search bar
        searchField.font = searchField.font.deriveFont(13f)
        searchField.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1),
            EmptyBorder(8, 12, 8, 12)
        )
        searchField.maximumSize = Dimension(Int.MAX_VALUE, 36)
        searchField.toolTipText = "Search libraries..."
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
            private fun onChange() {
                searchQuery = searchField.text.trim()
                refreshUI()
            }
        })

        // Wrap search horizontally
        val searchWrapper = JPanel()
        searchWrapper.layout = BoxLayout(searchWrapper, BoxLayout.X_AXIS)
        searchWrapper.background = JBColor.background()
        searchWrapper.add(searchField)

        val searchPanel = JPanel(BorderLayout()).apply {
            background = JBColor.background()
            border = EmptyBorder(12, 0, 0, 0)
            add(searchWrapper, BorderLayout.WEST)
        }

        // Setup title + subtitle (unchanged)
        val titlePanel = JPanel()
        titlePanel.layout = BoxLayout(titlePanel, BoxLayout.Y_AXIS)
        titlePanel.background = JBColor.background()

        // Title/subtitle now HTML for wrapping
        val title = JLabel("<html><b>FTC Library Manager</b></html>")
        title.font = title.font.deriveFont(22f).deriveFont(Font.BOLD)
        title.foreground = JBColor.foreground()
        title.alignmentX = Component.LEFT_ALIGNMENT

        val subtitle = JLabel("<html>Manage your FTC libraries with ease</html>")
        subtitle.font = subtitle.font.deriveFont(13f)
        subtitle.foreground = JBColor.GRAY
        subtitle.alignmentX = Component.LEFT_ALIGNMENT

        titlePanel.add(title)
        titlePanel.add(Box.createVerticalStrut(4))
        titlePanel.add(subtitle)

        // Assemble header content
        val headerContent = JPanel()
        headerContent.layout = BoxLayout(headerContent, BoxLayout.Y_AXIS)
        headerContent.background = JBColor.background()
        headerContent.add(titlePanel)
        headerContent.add(searchPanel)

        headerPanel.add(headerContent, BorderLayout.WEST)
        add(headerPanel, BorderLayout.NORTH)

        // Setup main scrollable panel
        val scrollPane = JScrollPane(mainPanel)
        scrollPane.border = EmptyBorder(0, 0, 0, 0)
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER

        // REPLACED broken custom wheel handler with native faster scrolling
        scrollPane.verticalScrollBar.unitIncrement = 32
        scrollPane.verticalScrollBar.blockIncrement = 160
        scrollPane.viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE
        // (Removed previous addMouseWheelListener that consumed events)

        add(scrollPane, BorderLayout.CENTER)
        refreshUI()
    }

    private fun refreshUI() {
        mainPanel.removeAll()
        val c = GridBagConstraints().apply {
            gridx = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
            insets = Insets(0, 0, 0, 0)
        }

        val installedDeps = GradleInserter.getInstalledDependencies(project)
        val installedPrefixes = installedDeps.map { it.substringBeforeLast(":") }.toSet()
        val rawLibraries = LibraryRegistry.LIBRARIES

        val filtered = if (searchQuery.isBlank()) rawLibraries else {
            rawLibraries.filter { (n, s) ->
                n.contains(searchQuery, true) ||
                    s.description.contains(searchQuery, true) ||
                    s.group.contains(searchQuery, true) ||
                    s.artifact.contains(searchQuery, true) ||
                    s.subArtifactCoordinates.any { it.contains(searchQuery, true) }
            }
        }

        val installedEntries = filtered.filter { (_, src) ->
            when {
                src.subArtifactCoordinates.isNotEmpty() ->
                    src.subArtifactCoordinates.any { installedPrefixes.contains(it) }
                src.subArtifacts.isNotEmpty() ->
                    src.subArtifacts.any { installedPrefixes.contains("${src.group}:$it") }
                else -> installedPrefixes.contains("${src.group}:${src.artifact}")
            }
        }

        // Installed header
        c.gridy = 0
        mainPanel.add(createSectionHeader("Installed", installedEntries.size), c)
        c.gridy++
        mainPanel.add(Box.createVerticalStrut(8), c); c.gridy++

        if (installedEntries.isEmpty()) {
            mainPanel.add(createEmptyState("No libraries installed", "Install from the Available section below"), c)
            c.gridy++
            mainPanel.add(Box.createVerticalStrut(16), c); c.gridy++
        } else {
            installedEntries.forEach { (name, src) ->
                val version = if (src.subArtifactCoordinates.isNotEmpty()) {
                    val coord = src.subArtifactCoordinates.firstOrNull { installedPrefixes.contains(it) }
                    coord?.let { installedDeps.firstOrNull { dep -> dep.startsWith("$it:") }?.substringAfterLast(":") }
                } else {
                    installedDeps.firstOrNull { it.startsWith("${src.group}:${src.artifact}:") }?.substringAfterLast(":")
                }
                val card = makeInstalledCard(name, src, version)
                mainPanel.add(card, c); c.gridy++
                mainPanel.add(Box.createVerticalStrut(12), c); c.gridy++
            }
        }

        // Available header
        val availableEntries = filtered.filter { (name, src) ->
            if (name == "SolversLib Pedro Pathing") {
                val pedroInstalled = installedPrefixes.any { it.startsWith("com.pedropathing:ftc") }
                if (!pedroInstalled) return@filter false
            }
            when {
                src.subArtifactCoordinates.isNotEmpty() ->
                    src.subArtifactCoordinates.any { !installedPrefixes.contains(it) }
                src.subArtifacts.isNotEmpty() ->
                    src.subArtifacts.any { !installedPrefixes.contains("${src.group}:$it") }
                else -> !installedPrefixes.contains("${src.group}:${src.artifact}")
            }
        }

        mainPanel.add(Box.createVerticalStrut(8), c); c.gridy++
        mainPanel.add(createSectionHeader("Available", availableEntries.size), c); c.gridy++
        mainPanel.add(Box.createVerticalStrut(8), c); c.gridy++

        if (availableEntries.isEmpty()) {
            mainPanel.add(createEmptyState("No libraries available", "All are installed or filtered out"), c); c.gridy++
            mainPanel.add(Box.createVerticalStrut(16), c); c.gridy++
        } else {
            availableEntries.forEach { (name, src) ->
                val card = makeAvailableCard(name, src, installedPrefixes)
                mainPanel.add(card, c); c.gridy++
                mainPanel.add(Box.createVerticalStrut(12), c); c.gridy++
            }
        }

        // Push top
        c.weighty = 1.0
        mainPanel.add(Box.createVerticalGlue(), c)

        mainPanel.revalidate()
        mainPanel.repaint()
    }

    private fun makeAvailableCard(name: String, src: LibrarySource, installedPrefixes: Set<String>): JPanel {
        val header = JLabel(name) // REMOVED HTML wrapper
        header.font = header.font.deriveFont(Font.BOLD, 14f)
        header.alignmentX = Component.LEFT_ALIGNMENT

        val descComponent = createDescription(src.description)

        val coordinateMode = src.subArtifactCoordinates.isNotEmpty()
        val selectable = if (coordinateMode) {
            src.subArtifactCoordinates.filterNot { installedPrefixes.contains(it) }
        } else if (src.subArtifacts.isNotEmpty() && name != "Pedro Pathing") {
            src.subArtifacts.filterNot { installedPrefixes.contains("${src.group}:$it") }
        } else emptyList()

        val artifactSelector: JComboBox<String>? =
            if (selectable.isNotEmpty()) {
                JComboBox(selectable.toTypedArray()).apply {
                    maximumSize = Dimension(Int.MAX_VALUE, 32)
                    alignmentX = Component.LEFT_ALIGNMENT
                    if (selectable.contains("dev.frozenmilk.dairy:Core")) selectedItem = "dev.frozenmilk.dairy:Core"
                    if (selectable.contains("dev.nextftc:ftc")) selectedItem = "dev.nextftc:ftc"
                    if (selectable.contains("fullpanels")) selectedItem = "fullpanels"
                }
            } else null

        val card = createCard()

        val versionSelector = JComboBox<String>().apply {
            maximumSize = Dimension(Int.MAX_VALUE, 32)
            addItem("Loading...")
            alignmentX = Component.LEFT_ALIGNMENT
        }

        fun loadVersions(selection: String) {
            versionSelector.removeAllItems()
            versionSelector.addItem("Loading...")
            Thread {
                try {
                    val (group, artifact) = if (coordinateMode && selection.contains(":")) {
                        selection.substringBefore(":") to selection.substringAfter(":")
                    } else src.group to selection
                    val effectiveSrc = src.copy(group = group, artifact = artifact)
                    val versions = VersionFetcher.fetchVersions(effectiveSrc)
                    SwingUtilities.invokeLater {
                        versionSelector.removeAllItems()
                        if (versions.isEmpty()) versionSelector.addItem("No versions found") else {
                            versions.forEach { versionSelector.addItem(it) }
                            versionSelector.selectedIndex = versions.size - 1
                        }
                    }
                } catch (_: Exception) {
                    SwingUtilities.invokeLater {
                        versionSelector.removeAllItems()
                        versionSelector.addItem("Error loading")
                    }
                }
            }.start()
        }

        val initial = artifactSelector?.selectedItem?.toString()
            ?: if (coordinateMode) src.subArtifactCoordinates.first() else src.artifact
        loadVersions(initial)

        artifactSelector?.addActionListener {
            val sel = artifactSelector.selectedItem?.toString() ?: return@addActionListener
            loadVersions(sel)
        }

        val installButton = createButton("Install", primaryColor).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            addActionListener {
                val selVersion = versionSelector.selectedItem?.toString() ?: return@addActionListener
                if (!selVersion.contains(".")) return@addActionListener
                if (name == "Pedro Pathing" && src.subArtifacts.isNotEmpty()) {
                    src.subArtifacts.forEach { a ->
                        GradleInserter.insertDependency(project, "${src.group}:$a:$selVersion", src.repositories)
                    }
                    showSuccessMessage("$name modules installed ($selVersion)")
                } else {
                    val sel = artifactSelector?.selectedItem?.toString()
                        ?: if (coordinateMode) src.subArtifactCoordinates.first() else src.artifact
                    val (group, artifact) = if (coordinateMode && sel.contains(":")) {
                        sel.substringBefore(":") to sel.substringAfter(":")
                    } else src.group to sel
                    GradleInserter.insertDependency(project, "$group:$artifact:$selVersion", src.repositories)
                    showSuccessMessage("$artifact $selVersion installed")
                }
                refreshUI()
            }
        }

        val content = Box.createVerticalBox()
        content.alignmentX = Component.LEFT_ALIGNMENT
        content.add(header)
        content.add(Box.createVerticalStrut(6))
        content.add(descComponent)
        content.add(Box.createVerticalStrut(10))
        if (artifactSelector != null) {
            content.add(createRow("Module:", artifactSelector))
            content.add(Box.createVerticalStrut(8))
        }
        content.add(createRow("Version:", versionSelector, installButton))

        card.add(content)
        return card
    }

    private fun makeInstalledCard(name: String, src: LibrarySource, currentVersion: String?): JPanel {
        val card = createCard()

        val multiModuleVersions: Map<String, String> = if (name == "Pedro Pathing" && src.subArtifacts.isNotEmpty()) {
            val installed = GradleInserter.getInstalledDependencies(project)
            src.subArtifacts.associateWith { module ->
                installed.firstOrNull { it.startsWith("${src.group}:$module:") }
                    ?.substringAfterLast(":") ?: "—"
            }
        } else emptyMap()

        val header = JLabel(name).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val versionBadgeText = if (multiModuleVersions.isNotEmpty()) {
            // Show primary module (ftc) version; if telemetry differs, indicate mixed
            val ftcV = multiModuleVersions["ftc"] ?: "—"
            val teleV = multiModuleVersions["telemetry"] ?: "—"
            if (ftcV == teleV || teleV == "—") "v$ftcV" else "ftc $ftcV / telemetry $teleV"
        } else "v$currentVersion"

        val versionBadge = JLabel(" $versionBadgeText ").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color.WHITE
            background = successColor
            isOpaque = true
            border = EmptyBorder(2, 6, 2, 6)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val descComponent = createDescription(src.description)

        val updateButton = createButton("Update", warningColor)
        val deleteButton = createButton("Remove", dangerColor)

        updateButton.addActionListener {
            updateButton.isEnabled = false
            updateButton.text = "Checking..."
            Thread {
                val baseSource = if (name == "Pedro Pathing") src.copy(artifact = "ftc") else src
                val versions = VersionFetcher.fetchVersions(baseSource)
                if (versions.isEmpty()) {
                    SwingUtilities.invokeLater {
                        updateButton.isEnabled = true
                        updateButton.text = "Update"
                        showErrorMessage("Could not fetch versions for $name")
                    }
                    return@Thread
                }
                val latest = versions.last()
                SwingUtilities.invokeLater {
                    if (name == "Pedro Pathing" && multiModuleVersions.isNotEmpty()) {
                        val currentFtc = multiModuleVersions["ftc"]
                        if (currentFtc == latest) {
                            showInfoMessage("$name is already at latest ($latest)")
                        } else {
                            // Update both modules to same latest version
                            src.subArtifacts.forEach { mod ->
                                GradleInserter.insertDependency(project, "${src.group}:$mod:$latest", src.repositories)
                            }
                            showSuccessMessage("$name modules updated to $latest")
                        }
                    } else {
                        if (latest == currentVersion) {
                            showInfoMessage("$name is already at latest ($latest)")
                        } else {
                            GradleInserter.insertDependency(project, "${src.group}:${src.artifact}:$latest", src.repositories)
                            showSuccessMessage("$name updated to $latest")
                        }
                    }
                    updateButton.isEnabled = true
                    updateButton.text = "Update"
                    refreshUI()
                }
            }.start()
        }

        deleteButton.addActionListener {
            val confirm = JOptionPane.showConfirmDialog(
                this,
                "Remove $name?",
                "Confirm Removal",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )
            if (confirm == JOptionPane.YES_OPTION) {
                if (name == "Pedro Pathing" && src.subArtifacts.isNotEmpty()) {
                    src.subArtifacts.forEach { mod ->
                        GradleInserter.deleteDependency(project, "${src.group}:$mod")
                    }
                    showSuccessMessage("$name modules removed")
                } else {
                    GradleInserter.deleteDependency(project, "${src.group}:${src.artifact}")
                    showSuccessMessage("$name removed")
                }
                refreshUI()
            }
        }

        val buttons = createRow(updateButton, deleteButton)

        val content = Box.createVerticalBox()
        content.add(header)
        content.add(Box.createVerticalStrut(4))
        content.add(versionBadge)
        content.add(Box.createVerticalStrut(8))
        content.add(descComponent)

        if (multiModuleVersions.isNotEmpty()) {
            val modulesLabel = JLabel(
                "<html><i>Modules:</i> ftc (${multiModuleVersions["ftc"]}), telemetry (${multiModuleVersions["telemetry"]})</html>"
            )
            modulesLabel.font = modulesLabel.font.deriveFont(11f)
            modulesLabel.foreground = JBColor.GRAY
            content.add(Box.createVerticalStrut(6))
            content.add(modulesLabel)
        }

        content.add(Box.createVerticalStrut(12))
        content.add(buttons)

        card.add(content)
        return card
    }

    // NEW: simplified stable description without HTML width tricks; wraps fully
    private fun createDescription(text: String): JComponent {
        val urlRegex = Regex("""https?://\S+""")
        val urls = urlRegex.findAll(text).map { it.value.removeSuffix(".") }.toList()
        val area = JTextArea(text).apply {
            lineWrap = true
            wrapStyleWord = true
            isEditable = false
            isOpaque = false
            border = null
            font = font.deriveFont(12f)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        if (urls.isEmpty()) return area

        val panel = Box.createVerticalBox()
        panel.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(area)
        panel.add(Box.createVerticalStrut(6))

        val linksRow = Box.createHorizontalBox()
        linksRow.alignmentX = Component.LEFT_ALIGNMENT
        urls.forEachIndexed { i, u ->
            val btn = JButton(if (i == 0) "Docs" else "Link ${i + 1}").apply {
                isFocusPainted = false
                font = font.deriveFont(11f)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener {
                    try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(u)) } catch (_: Exception) {}
                }
            }
            linksRow.add(btn)
            linksRow.add(Box.createHorizontalStrut(6))
        }
        panel.add(linksRow)
        return panel
    }

    private fun createCard(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = cardBackground
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor),
            EmptyBorder(14, 14, 14, 14)
        )
        panel.alignmentX = Component.LEFT_ALIGNMENT
        return panel
    }

    // ADD: helper to create styled buttons
    private fun createButton(text: String, color: Color): JButton {
        return JButton(text).apply {
            isFocusPainted = false
            background = color
            foreground = Color.WHITE
            font = font.deriveFont(12f).deriveFont(Font.BOLD)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 1, true),
                EmptyBorder(6, 12, 6, 12)
            )
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
    }

    // ADD: row without label
    private fun createRow(vararg components: Component): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.background = cardBackground
        components.forEachIndexed { i, c ->
            panel.add(c)
            if (i != components.lastIndex) panel.add(Box.createHorizontalStrut(8))
        }
        panel.alignmentX = Component.LEFT_ALIGNMENT
        return panel
    }

    // ADD: row with label
    private fun createRow(label: String, vararg components: Component): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.background = cardBackground
        val lbl = JLabel(label)
        lbl.font = lbl.font.deriveFont(Font.BOLD, 12f)
        panel.add(lbl)
        panel.add(Box.createHorizontalStrut(6))
        components.forEachIndexed { i, c ->
            panel.add(c)
            if (i != components.lastIndex) panel.add(Box.createHorizontalStrut(8))
        }
        panel.alignmentX = Component.LEFT_ALIGNMENT
        return panel
    }

    // ADD: section header
    private fun createSectionHeader(text: String, count: Int): JPanel {
        val panel = JPanel(BorderLayout())
        panel.background = JBColor.background()
        val title = JLabel("$text")
        title.font = title.font.deriveFont(Font.BOLD, 16f)
        val badge = JLabel(" $count ")
        badge.font = badge.font.deriveFont(Font.BOLD, 11f)
        badge.foreground = Color.WHITE
        badge.background = primaryColor
        badge.isOpaque = true
        badge.border = EmptyBorder(2, 6, 2, 6)
        val box = Box.createHorizontalBox()
        box.add(title)
        box.add(Box.createHorizontalStrut(8))
        box.add(badge)
        panel.add(box, BorderLayout.WEST)
        return panel
    }

    // ADD: empty state panel
    private fun createEmptyState(title: String, description: String): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = JBColor.background()
        val tLbl = JLabel(title)
        tLbl.font = tLbl.font.deriveFont(Font.BOLD, 14f)
        val dLbl = JLabel("<html><div style='width:100%;'>$description</div></html>")
        dLbl.font = dLbl.font.deriveFont(12f)
        dLbl.foreground = JBColor.GRAY
        panel.add(tLbl)
        panel.add(Box.createVerticalStrut(4))
        panel.add(dLbl)
        panel.alignmentX = Component.LEFT_ALIGNMENT
        return panel
    }

    // ADD: message helpers
    private fun showSuccessMessage(message: String) =
        JOptionPane.showMessageDialog(this, message, "Success", JOptionPane.INFORMATION_MESSAGE)

    private fun showErrorMessage(message: String) =
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE)

    private fun showInfoMessage(message: String) =
        JOptionPane.showMessageDialog(this, message, "Info", JOptionPane.PLAIN_MESSAGE)

    // ADDED: escapeHtml helper (was missing causing compile error)
    private fun escapeHtml(s: String): String = buildString {
        for (ch in s) {
            when (ch) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
}
