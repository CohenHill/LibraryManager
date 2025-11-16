package org.example.librarymanager.ftc

data class LibrarySource(
    val group: String,
    val artifact: String,
    val repoUrl: String,
    val repositories: List<String> = emptyList(), // All repos to ensure in build.dependencies.gradle
    val subArtifacts: List<String> = emptyList(),           // FullPanels style (same group)
    val subArtifactCoordinates: List<String> = emptyList(), // NEW: list of "group:artifact" for mixed-group suites (Dairy/NextFTC)
    val description: String = "", // Human-readable description of the library
    val category: String? = null // NEW: "DAIRY" or "NEXTFTC"
)