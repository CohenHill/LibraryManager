package org.example.librarymanager.ftc

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import java.awt.*
import java.net.URI
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent

class FTCLibPanel(private val project: Project) : JPanel() {

    private val mainPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = JBColor.background()
    }
    private var searchQuery = ""

    private val primaryColor = JBColor(Color(0x007ACC), Color(0x0098FF))
    private val successColor = JBColor(Color(0x28A745), Color(0x2EA043))
    private val dangerColor = JBColor(Color(0xDC3545), Color(0xF85149))
    private val warningColor = JBColor(Color(0xFFC107), Color(0xD29922))
    private val cardBackground = JBColor(Color(0xF5F5F5), Color(0x2B2B2B))
    private val borderColor = JBColor(Color(0xE0E0E0), Color(0x3C3C3C))

    private val searchField = JTextField()
    private var hasUpdatesAvailable = false
    private val placeholderText = "Search libraries..."
    private var showingPlaceholder = true

    init {
        layout = BorderLayout()
        background = JBColor.background()
        border = EmptyBorder(16, 8, 16, 8)

        val headerPanel = JPanel(BorderLayout())
        headerPanel.background = JBColor.background()
        headerPanel.border = EmptyBorder(0, 0, 16, 0)

        searchField.font = searchField.font.deriveFont(13f)
        searchField.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1),
            EmptyBorder(8, 12, 8, 12)
        )
        searchField.preferredSize = Dimension(300, 36)
        searchField.minimumSize = Dimension(50, 36)
        searchField.toolTipText = "Search libraries..."

        // Set initial placeholder
        searchField.text = placeholderText
        searchField.foreground = JBColor.GRAY
        showingPlaceholder = true

        // Add focus listener for placeholder behavior
        searchField.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                if (showingPlaceholder) {
                    searchField.text = ""
                    searchField.foreground = JBColor.foreground()
                    showingPlaceholder = false
                }
            }

            override fun focusLost(e: FocusEvent?) {
                if (searchField.text.trim().isEmpty()) {
                    searchField.text = placeholderText
                    searchField.foreground = JBColor.GRAY
                    showingPlaceholder = true
                }
            }
        })

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
            private fun onChange() {
                if (!showingPlaceholder) {
                    searchQuery = searchField.text.trim()
                    refreshUI()
                }
            }
        })

        val searchPanel = JPanel(BorderLayout()).apply {
            background = JBColor.background()
            border = EmptyBorder(12, 0, 0, 0)
            add(searchField, BorderLayout.CENTER)
        }

        val titlePanel = JPanel()
        titlePanel.layout = BoxLayout(titlePanel, BoxLayout.Y_AXIS)
        titlePanel.background = JBColor.background()

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

        val headerContent = JPanel()
        headerContent.layout = BoxLayout(headerContent, BoxLayout.Y_AXIS)
        headerContent.background = JBColor.background()
        headerContent.add(titlePanel)
        headerContent.add(searchPanel)

        headerPanel.add(headerContent, BorderLayout.CENTER)
        add(headerPanel, BorderLayout.NORTH)

        val scrollPane = JScrollPane(mainPanel)
        scrollPane.border = EmptyBorder(0, 0, 0, 0)
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.verticalScrollBar.unitIncrement = 32
        scrollPane.verticalScrollBar.blockIncrement = 160
        scrollPane.viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE

        add(scrollPane, BorderLayout.CENTER)
        refreshUI()
    }

    private fun refreshUI() {
        mainPanel.removeAll()
        hasUpdatesAvailable = false

        val installedDeps = GradleInserter.getInstalledDependencies(project)
        val installedPrefixes = installedDeps.map { it.substringBeforeLast(":") }.toSet()
        val rawLibraries = LibraryRegistry.LIBRARIES

        val hasAnyIncompatibility = rawLibraries.keys.any { libName ->
            getIncompatibilitiesFor(libName, installedPrefixes).isNotEmpty()
        }

        updateToolWindowIcon(hasAnyIncompatibility, hasUpdatesAvailable)

        val filtered = if (searchQuery.isBlank()) rawLibraries else {
            rawLibraries.filter { (n, s) ->
                n.contains(searchQuery, true) ||
                    s.description.contains(searchQuery, true) ||
                    s.group.contains(searchQuery, true) ||
                    s.artifact.contains(searchQuery, true) ||
                    s.subArtifactCoordinates.any { it.contains(searchQuery, true) }
            }
        }

        val installedCards = mutableListOf<Pair<String, JPanel>>()

        filtered.forEach { (name, src) ->
            if (src.subArtifactCoordinates.isNotEmpty()) {
                src.subArtifactCoordinates.forEach { coord ->
                    if (installedPrefixes.contains(coord)) {
                        val version = installedDeps.firstOrNull { it.startsWith("$coord:") }?.substringAfterLast(":")
                        val (g, a) = coord.split(":")
                        val displayName = when {
                            name == "Dairy Suite" -> "Dairy ${formatArtifactName(a)}"
                            name == "NextFTC Suite" -> "NextFTC ${formatArtifactName(a)}"
                            else -> "${name} ${formatArtifactName(a)}"
                        }
                        val tempSrc = src.copy(group = g, artifact = a)
                        val card = makeInstalledCard(displayName, tempSrc, version, parentSuite = name)
                        installedCards.add(displayName to card)
                    }
                }
            } else if (src.subArtifacts.isNotEmpty()) {
                src.subArtifacts.forEach { a ->
                    val prefix = "${src.group}:$a"
                    if (installedPrefixes.contains(prefix)) {
                        val version = installedDeps.firstOrNull { it.startsWith("$prefix:") }?.substringAfterLast(":")
                        val displayName = if (name == "Panels Library") {
                            "Panels ${formatArtifactName(a)}"
                        } else {
                            "$name ${formatArtifactName(a)}"
                        }
                        val tempSrc = src.copy(artifact = a)
                        val card = makeInstalledCard(displayName, tempSrc, version, parentSuite = name)
                        installedCards.add(displayName to card)
                    }
                }
            } else {
                if (installedPrefixes.contains("${src.group}:${src.artifact}")) {
                    val version = installedDeps.firstOrNull { it.startsWith("${src.group}:${src.artifact}:") }?.substringAfterLast(":")
                    val card = makeInstalledCard(name, src, version)
                    installedCards.add(name to card)
                }
            }
        }

        addSection("Installed", installedCards.size) {
            if (installedCards.isEmpty()) {
                addEmptyState("No libraries installed", "Install from the Available section below")
            } else {
                installedCards.sortedBy { it.first }.forEach { (_, card) ->
                    addCard(card)
                }
            }
        }

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

        mainPanel.add(Box.createVerticalStrut(8))

        addSection("Available", availableEntries.size) {
            if (availableEntries.isEmpty()) {
                addEmptyState("No libraries available", "All are installed or filtered out")
            } else {
                availableEntries.forEach { (name, src) ->
                    val card = makeAvailableCard(name, src, installedPrefixes)
                    addCard(card)
                }
            }
        }

        mainPanel.add(Box.createVerticalGlue())
        mainPanel.revalidate()
        mainPanel.repaint()
    }

    private fun addSection(title: String, count: Int, content: () -> Unit) {
        val header = createSectionHeader(title, count)
        header.alignmentX = Component.LEFT_ALIGNMENT
        header.maximumSize = Dimension(Int.MAX_VALUE, header.preferredSize.height)
        mainPanel.add(header)
        mainPanel.add(Box.createVerticalStrut(8))
        content()
    }

    private fun addCard(card: JPanel) {
        card.alignmentX = Component.LEFT_ALIGNMENT
        card.maximumSize = Dimension(Int.MAX_VALUE, card.preferredSize.height)
        mainPanel.add(card)
        mainPanel.add(Box.createVerticalStrut(12))
    }

    private fun addEmptyState(title: String, description: String) {
        val emptyState = createEmptyState(title, description)
        emptyState.alignmentX = Component.LEFT_ALIGNMENT
        emptyState.maximumSize = Dimension(Int.MAX_VALUE, emptyState.preferredSize.height)
        mainPanel.add(emptyState)
        mainPanel.add(Box.createVerticalStrut(16))
    }

    private fun formatArtifactName(artifact: String): String {
        return when (artifact.lowercase()) {
            "core" -> "Core"
            "mercurial" -> "Mercurial"
            "pasteurized" -> "Pasteurized"
            "sinister" -> "Sinister"
            "sloth" -> "Sloth"
            "util" -> "Util"
            "ftc" -> "Core"
            "hardware" -> "Hardware"
            "control" -> "Control"
            "bindings" -> "Bindings"
            "pedro" -> "Pedro Extension"
            "roadrunner" -> "Road Runner Extension"
            "fateweaver" -> "FateWeaver Extension"
            "fullpanels" -> "Full Bundle"
            "battery" -> "Battery"
            "camerastream" -> "Camera Stream"
            "capture" -> "Capture"
            "configurables" -> "Configurables"
            "field" -> "Field"
            "gamepad" -> "Gamepad"
            "graph" -> "Graph"
            "lights" -> "Lights"
            "limelightproxy" -> "Limelight Proxy"
            "opmodecontrol" -> "OpMode Control"
            "pinger" -> "Pinger"
            "telemetry" -> "Telemetry"
            "themes" -> "Themes"
            "utils" -> "Utils"
            "dashboard" -> "Dashboard"
            else -> artifact.split("-", "_").joinToString(" ") {
                it.replaceFirstChar { c -> c.uppercase() }
            }
        }
    }

    private fun updateToolWindowIcon(hasIncompatibility: Boolean, hasUpdates: Boolean) {
        SwingUtilities.invokeLater {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("FTCLibManager")
            if (toolWindow != null) {
                val iconPath = when {
                    hasIncompatibility -> "/icons/ftcWarning.svg"
                    hasUpdates -> "/icons/ftcUpdate.svg"
                    else -> "/icons/ftc.svg"
                }
                try {
                    val icon = com.intellij.openapi.util.IconLoader.getIcon(iconPath, javaClass)
                    toolWindow.setIcon(icon)
                } catch (e: Exception) {
                    println("Failed to load icon: $iconPath")
                }
            }
        }
    }

    private fun makeAvailableCard(name: String, src: LibrarySource, installedPrefixes: Set<String>): JPanel {
        val card = JPanel().apply {
            layout = BorderLayout(12, 12)
            background = cardBackground
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                EmptyBorder(14, 14, 14, 14)
            )
        }

        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = cardBackground
            isOpaque = false
        }

        val header = JLabel(name).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        contentPanel.add(header)
        contentPanel.add(Box.createVerticalStrut(6))

        val descComponent = createDescription(src.description)
        descComponent.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(descComponent)

        createIncompatibilitiesRow(name, installedPrefixes)?.let {
            contentPanel.add(Box.createVerticalStrut(8))
            it.alignmentX = Component.LEFT_ALIGNMENT
            contentPanel.add(it)
        }

        createSuggestionsRow(name, installedPrefixes)?.let {
            contentPanel.add(Box.createVerticalStrut(8))
            it.alignmentX = Component.LEFT_ALIGNMENT
            contentPanel.add(it)
        }

        contentPanel.add(Box.createVerticalStrut(10))

        val coordinateMode = src.subArtifactCoordinates.isNotEmpty()
        val selectable = if (coordinateMode) {
            src.subArtifactCoordinates.filterNot { installedPrefixes.contains(it) }
        } else if (src.subArtifacts.isNotEmpty() && name != "Pedro Pathing") {
            src.subArtifacts.filterNot { installedPrefixes.contains("${src.group}:$it") }
        } else emptyList()

        val artifactSelector: JComboBox<String>? =
            if (selectable.isNotEmpty()) {
                JComboBox(selectable.toTypedArray()).apply {
                    // REDUCED: Lower minimum for more shrinking
                    minimumSize = Dimension(80, 32) // Was 150
                    maximumSize = Dimension(Int.MAX_VALUE, 32)
                    if (selectable.contains("dev.frozenmilk.dairy:Core")) selectedItem = "dev.frozenmilk.dairy:Core"
                    if (selectable.contains("dev.nextftc:ftc")) selectedItem = "dev.nextftc:ftc"
                    if (selectable.contains("fullpanels")) selectedItem = "fullpanels"
                }
            } else null

        val versionSelector = JComboBox<String>().apply {
            // REDUCED: Lower minimum for more shrinking
            minimumSize = Dimension(60, 32) // Was 100
            maximumSize = Dimension(Int.MAX_VALUE, 32)
            addItem("Loading...")
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

        if (artifactSelector != null) {
            // FIXED: Use BorderLayout for module row to force proper sizing
            val moduleRow = JPanel(BorderLayout(6, 0)).apply {
                background = cardBackground
                val labelPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    background = cardBackground
                    add(JLabel("Module:").apply { font = font.deriveFont(Font.BOLD, 12f) })
                }
                add(labelPanel, BorderLayout.WEST)
                add(artifactSelector, BorderLayout.CENTER)
            }
            moduleRow.alignmentX = Component.LEFT_ALIGNMENT
            moduleRow.maximumSize = Dimension(Int.MAX_VALUE, 32)
            contentPanel.add(moduleRow)
            contentPanel.add(Box.createVerticalStrut(8))
        }

        // FIXED: Use BorderLayout for version row to force proper sizing
        val versionRow = JPanel(BorderLayout(6, 0)).apply {
            background = cardBackground
            val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                background = cardBackground
                add(JLabel("Version:").apply { font = font.deriveFont(Font.BOLD, 12f) })
            }
            add(leftPanel, BorderLayout.WEST)

            val centerPanel = JPanel(BorderLayout(6, 0)).apply {
                background = cardBackground
                add(versionSelector, BorderLayout.CENTER)
                add(installButton, BorderLayout.EAST)
            }
            add(centerPanel, BorderLayout.CENTER)
        }
        versionRow.alignmentX = Component.LEFT_ALIGNMENT
        versionRow.maximumSize = Dimension(Int.MAX_VALUE, 32)
        contentPanel.add(versionRow)

        card.add(contentPanel, BorderLayout.CENTER)
        return card
    }

    private fun makeInstalledCard(name: String, src: LibrarySource, currentVersion: String?, parentSuite: String? = null): JPanel {
        val card = JPanel().apply {
            layout = BorderLayout(12, 12)
            background = cardBackground
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                EmptyBorder(14, 14, 14, 14)
            )
        }

        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = cardBackground
            isOpaque = false
        }

        val header = JLabel(name).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        contentPanel.add(header)
        contentPanel.add(Box.createVerticalStrut(4))

        val versionBadge = JLabel(" v$currentVersion ").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color.WHITE
            background = successColor
            isOpaque = true
            border = EmptyBorder(2, 6, 2, 6)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        contentPanel.add(versionBadge)
        contentPanel.add(Box.createVerticalStrut(8))

        val descComponent = createDescription(src.description)
        descComponent.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(descComponent)

        val installedPrefixes = GradleInserter.getInstalledDependencies(project)
            .map { it.substringBeforeLast(":") }
            .toSet()

        val checkName = parentSuite ?: name
        createIncompatibilitiesRow(checkName, installedPrefixes)?.let {
            contentPanel.add(Box.createVerticalStrut(8))
            it.alignmentX = Component.LEFT_ALIGNMENT
            contentPanel.add(it)
        }

        createSuggestionsRow(checkName, installedPrefixes)?.let {
            contentPanel.add(Box.createVerticalStrut(8))
            it.alignmentX = Component.LEFT_ALIGNMENT
            contentPanel.add(it)
        }

        contentPanel.add(Box.createVerticalStrut(12))

        val updateButton = createButton("Update", warningColor)
        val deleteButton = createButton("Remove", dangerColor)

        Thread {
            try {
                val versions = VersionFetcher.fetchVersions(src)
                if (versions.isNotEmpty()) {
                    val latest = versions.last()
                    if (latest != currentVersion) {
                        SwingUtilities.invokeLater {
                            hasUpdatesAvailable = true
                            val installedDeps = ApplicationManager.getApplication().runReadAction<List<String>> {
                                GradleInserter.getInstalledDependencies(project)
                            }
                            val installedPrefixes = installedDeps.map { it.substringBeforeLast(":") }.toSet()
                            val hasAnyIncompatibility = LibraryRegistry.LIBRARIES.keys.any { libName ->
                                getIncompatibilitiesFor(libName, installedPrefixes).isNotEmpty()
                            }
                            updateToolWindowIcon(hasAnyIncompatibility, hasUpdatesAvailable)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }.start()

        updateButton.addActionListener {
            updateButton.isEnabled = false
            updateButton.text = "Checking..."
            Thread {
                val versions = VersionFetcher.fetchVersions(src)
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
                    if (latest == currentVersion) {
                        showInfoMessage("$name is already at latest ($latest)")
                    } else {
                        GradleInserter.insertDependency(project, "${src.group}:${src.artifact}:$latest", src.repositories)
                        showSuccessMessage("$name updated to $latest")
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
                GradleInserter.deleteDependency(project, "${src.group}:${src.artifact}")
                showSuccessMessage("$name removed")
                refreshUI()
            }
        }

        val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            background = cardBackground
            add(updateButton)
            add(deleteButton)
        }
        buttonRow.alignmentX = Component.LEFT_ALIGNMENT
        contentPanel.add(buttonRow)

        card.add(contentPanel, BorderLayout.CENTER)
        return card
    }

    private fun getSuggestionsFor(libName: String): List<String> = when (libName) {
        "Pedro Pathing" -> listOf("Panels Library", "FTC Dashboard")
        "Road Runner Core" -> listOf("FTC Dashboard")
        "Hermes", "Koala Log" -> listOf("FTC Dashboard")
        else -> emptyList()
    }

    private fun isInstalled(src: LibrarySource, installedPrefixes: Set<String>): Boolean {
        return when {
            src.subArtifactCoordinates.isNotEmpty() ->
                src.subArtifactCoordinates.any { installedPrefixes.contains(it) }
            src.subArtifacts.isNotEmpty() ->
                src.subArtifacts.any { installedPrefixes.contains("${src.group}:$it") }
            else -> installedPrefixes.contains("${src.group}:${src.artifact}")
        }
    }

    private fun createSuggestionsRow(forName: String, installedPrefixes: Set<String>): JComponent? {
        val staticItems = getSuggestionsFor(forName)
            .mapNotNull { n -> LibraryRegistry.LIBRARIES[n]?.let { n to it } }
            .filter { (_, src) -> !isInstalled(src, installedPrefixes) }
            .toMutableList()

        val dynamic = mutableListOf<Pair<String, LibrarySource>>()

        val hasNextFTCCore = installedPrefixes.contains("dev.nextftc:ftc")
        val hasPedro = installedPrefixes.contains("com.pedropathing:ftc") || installedPrefixes.contains("com.pedropathing:telemetry")
        val hasRR = installedPrefixes.contains("com.github.acmerobotics:road-runner")
        val hasFate = installedPrefixes.contains("gay.zharel.fateweaver:ftc")

        val hasNextPedroExt = installedPrefixes.contains("dev.nextftc.extensions:pedro")
        val hasNextRRExt = installedPrefixes.contains("dev.nextftc.extensions:roadrunner")
        val hasNextFateExt = installedPrefixes.contains("dev.nextftc.extensions:fateweaver")

        val hasSolversCore = installedPrefixes.contains("org.solverslib:core")
        val hasSolversPedro = installedPrefixes.contains("org.solverslib:pedroPathing")

        if (hasNextFTCCore && !hasNextPedroExt && (forName == "Pedro Pathing" || forName == "NextFTC Suite") && hasPedro) {
            dynamic += "NextFTC Pedro Extension" to LibrarySource(
                group = "dev.nextftc.extensions",
                artifact = "pedro",
                repoUrl = "https://repo1.maven.org/maven2",
                repositories = emptyList(),
                description = "NextFTC extension for Pedro Pathing"
            )
        }
        if (hasNextFTCCore && !hasNextRRExt && (forName == "Road Runner Core" || forName == "NextFTC Suite") && hasRR) {
            dynamic += "NextFTC Road Runner Extension" to LibrarySource(
                group = "dev.nextftc.extensions",
                artifact = "roadrunner",
                repoUrl = "https://repo1.maven.org/maven2",
                repositories = emptyList(),
                description = "NextFTC extension for Road Runner"
            )
        }
        if (hasNextFTCCore && !hasNextFateExt && (forName == "FateWeaver" || forName == "NextFTC Suite") && hasFate) {
            dynamic += "NextFTC FateWeaver Extension" to LibrarySource(
                group = "dev.nextftc.extensions",
                artifact = "fateweaver",
                repoUrl = "https://repo1.maven.org/maven2",
                repositories = emptyList(),
                description = "NextFTC extension for FateWeaver"
            )
        }

        if (!hasSolversPedro && hasPedro && hasSolversCore && (forName == "Pedro Pathing" || forName == "SolversLib Core")) {
            LibraryRegistry.LIBRARIES["SolversLib Pedro Pathing"]?.let { src ->
                if (!isInstalled(src, installedPrefixes)) {
                    dynamic += "SolversLib Pedro Pathing" to src
                }
            }
        }

        val items = (staticItems + dynamic)
        if (items.isEmpty()) return null

        val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            background = cardBackground
        }

        row.add(JLabel("Suggested:").apply {
            font = font.deriveFont(Font.BOLD, 12f)
        })

        items.forEach { (name, src) ->
            val btn = JButton(name).apply {
                isFocusPainted = false
                font = font.deriveFont(11f).deriveFont(Font.BOLD)
                background = primaryColor
                foreground = Color.WHITE
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(primaryColor.darker(), 1, true),
                    EmptyBorder(4, 10, 4, 10)
                )
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener {
                    isEnabled = false
                    text = "Installing..."
                    Thread {
                        try {
                            val (g, a) = when {
                                src.group == "com.bylazar" && (src.subArtifacts.contains("fullpanels") || src.artifact == "fullpanels") ->
                                    src.group to "fullpanels"
                                else -> src.group to src.artifact
                            }
                            val effective = src.copy(group = g, artifact = a)
                            val versions = VersionFetcher.fetchVersions(effective)
                            val latest = versions.lastOrNull()
                            if (latest == null) {
                                SwingUtilities.invokeLater {
                                    text = name
                                    isEnabled = true
                                    showErrorMessage("No versions found for $name")
                                }
                            } else {
                                GradleInserter.insertDependency(project, "$g:$a:$latest", src.repositories)
                                SwingUtilities.invokeLater {
                                    showSuccessMessage("$name $latest installed")
                                    refreshUI()
                                }
                            }
                        } catch (_: Exception) {
                            SwingUtilities.invokeLater {
                                text = name
                                isEnabled = true
                                showErrorMessage("Failed to install $name")
                            }
                        }
                    }.start()
                }
            }
            row.add(btn)
        }

        return row
    }

    private fun getIncompatibilitiesFor(libName: String, installedPrefixes: Set<String>): List<Incompatibility> {
        if (libName != "Dairy Suite" && !installedPrefixes.contains("dev.frozenmilk.sinister:Sloth")) {
            return emptyList()
        }

        val hasSloth = installedPrefixes.contains("dev.frozenmilk.sinister:Sloth")
        val hasFTCDash = installedPrefixes.contains("com.github.acmerobotics:ftc-dashboard")
        val hasPanels = installedPrefixes.any { it.startsWith("com.bylazar:") }

        val conflicts = mutableListOf<Incompatibility>()

        if (hasSloth && hasFTCDash && (libName == "Dairy Suite" || libName == "FTC Dashboard")) {
            conflicts.add(Incompatibility(
                conflictingLib = if (libName == "Dairy Suite") "FTC Dashboard" else "Sloth (from Dairy Suite)",
                reason = "Sloth and FTC Dashboard cannot be used together",
                suggestedFix = "SlothDash"
            ))
        }

        if (hasSloth && hasPanels && (libName == "Dairy Suite" || libName == "Panels Library")) {
            conflicts.add(Incompatibility(
                conflictingLib = if (libName == "Dairy Suite") "Panels Library" else "Sloth (from Dairy Suite)",
                reason = "Sloth and Panels Library are incompatible",
                suggestedFix = null
            ))
        }

        return conflicts
    }

    data class Incompatibility(
        val conflictingLib: String,
        val reason: String,
        val suggestedFix: String?
    )

    private fun createIncompatibilitiesRow(forName: String, installedPrefixes: Set<String>): JComponent? {
        val conflicts = getIncompatibilitiesFor(forName, installedPrefixes)
        if (conflicts.isEmpty()) return null

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = cardBackground
            isOpaque = false
        }

        val headerRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            background = cardBackground
            add(JLabel("⚠️").apply { font = font.deriveFont(14f) })
            add(JLabel(" Incompatible:").apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = dangerColor
            })
        }
        panel.add(headerRow)
        panel.add(Box.createVerticalStrut(6))

        conflicts.forEach { conflict ->
            val conflictPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = JBColor(Color(0xFFF3CD), Color(0x3D2800))
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(warningColor, 2),
                    EmptyBorder(8, 10, 8, 10)
                )
            }

            conflictPanel.add(JLabel("⚠ ${conflict.reason}").apply {
                font = font.deriveFont(Font.BOLD, 11f)
                foreground = JBColor(Color(0x856404), Color(0xFFD700))
            })

            conflictPanel.add(Box.createVerticalStrut(4))
            conflictPanel.add(JLabel("Conflicts with: ${conflict.conflictingLib}").apply {
                font = font.deriveFont(11f)
                foreground = JBColor.GRAY
            })

            if (conflict.suggestedFix != null) {
                conflictPanel.add(Box.createVerticalStrut(6))
                val fixButton = JButton("Install ${conflict.suggestedFix}").apply {
                    isFocusPainted = false
                    font = font.deriveFont(11f).deriveFont(Font.BOLD)
                    background = successColor
                    foreground = Color.WHITE
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(successColor.darker(), 1, true),
                        EmptyBorder(4, 10, 4, 10)
                    )
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    addActionListener {
                        isEnabled = false
                        text = "Installing..."
                        Thread {
                            try {
                                val slothDashSrc = LibraryRegistry.LIBRARIES[conflict.suggestedFix]
                                if (slothDashSrc != null) {
                                    GradleInserter.deleteDependency(project, "com.github.acmerobotics:ftc-dashboard")
                                    val versions = VersionFetcher.fetchVersions(slothDashSrc)
                                    val latest = versions.lastOrNull()
                                    if (latest != null) {
                                        GradleInserter.insertDependency(
                                            project,
                                            "${slothDashSrc.group}:${slothDashSrc.artifact}:$latest",
                                            slothDashSrc.repositories
                                        )
                                        SwingUtilities.invokeLater {
                                            showSuccessMessage("${conflict.suggestedFix} $latest installed (FTC Dashboard removed)")
                                            refreshUI()
                                        }
                                    } else {
                                        SwingUtilities.invokeLater {
                                            text = "Install ${conflict.suggestedFix}"
                                            isEnabled = true
                                            showErrorMessage("No versions found for ${conflict.suggestedFix}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                SwingUtilities.invokeLater {
                                    text = "Install ${conflict.suggestedFix}"
                                    isEnabled = true
                                    showErrorMessage("Failed to install ${conflict.suggestedFix}")
                                }
                            }
                        }.start()
                    }
                }
                conflictPanel.add(fixButton)
            } else {
                conflictPanel.add(Box.createVerticalStrut(4))
                conflictPanel.add(JLabel("No automatic fix available. Manual resolution required.").apply {
                    font = font.deriveFont(Font.ITALIC, 10f)
                    foreground = JBColor.GRAY
                })
            }

            panel.add(conflictPanel)
            panel.add(Box.createVerticalStrut(6))
        }

        return panel
    }

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
            // REDUCED: Lower minimum for more shrinking
            minimumSize = Dimension(50, 20) // Was 100
        }

        // FIXED: Override getPreferredSize to respect parent width with lower minimum
        val wrappedArea = object : JPanel() {
            init {
                layout = BorderLayout()
                background = cardBackground
                isOpaque = false
                add(area, BorderLayout.CENTER)
            }

            override fun getPreferredSize(): Dimension {
                val parentWidth = parent?.width ?: 400
                val effectiveWidth = maxOf(50, parentWidth - 40) // REDUCED from 100
                area.size = Dimension(effectiveWidth, Short.MAX_VALUE.toInt())
                val pref = area.preferredSize
                return Dimension(effectiveWidth, pref.height)
            }
        }

        if (urls.isEmpty()) return wrappedArea

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = cardBackground
            isOpaque = false
        }

        wrappedArea.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(wrappedArea)
        panel.add(Box.createVerticalStrut(6))

        val linksRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            background = cardBackground
            maximumSize = Dimension(Int.MAX_VALUE, 30)
        }

        urls.forEachIndexed { i, u ->
            val btn = JButton(if (i == 0) "Docs" else "Link ${i + 1}").apply {
                isFocusPainted = false
                font = font.deriveFont(11f)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener {
                    try {
                        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(u))
                    } catch (_: Exception) {}
                }
            }
            linksRow.add(btn)
        }

        linksRow.alignmentX = Component.LEFT_ALIGNMENT
        panel.add(linksRow)
        return panel
    }

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
            minimumSize = Dimension(60, 32) // REDUCED from 80
        }
    }

    private fun createSectionHeader(text: String, count: Int): JPanel {
        val panel = JPanel(BorderLayout())
        panel.background = JBColor.background()
        val title = JLabel(text)
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

    private fun showSuccessMessage(message: String) =
        JOptionPane.showMessageDialog(this, message, "Success", JOptionPane.INFORMATION_MESSAGE)

    private fun showErrorMessage(message: String) =
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE)

    private fun showInfoMessage(message: String) =
        JOptionPane.showMessageDialog(this, message, "Info", JOptionPane.PLAIN_MESSAGE)
}
