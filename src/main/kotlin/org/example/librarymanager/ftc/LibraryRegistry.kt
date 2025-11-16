package org.example.librarymanager.ftc

object LibraryRegistry {

    // Fixed Dairy artifacts with correct repository paths
    private val dairyLibraries = mapOf(
        "Core" to Pair("dev.frozenmilk.dairy", "Core"),
        "Mercurial" to Pair("dev.frozenmilk.mercurial", "Mercurial"),
        "Pasteurized" to Pair("dev.frozenmilk.dairy", "Pasteurized"),
        "Sinister" to Pair("dev.frozenmilk", "Sinister"),
        "Sloth" to Pair("dev.frozenmilk.sinister", "Sloth"),
        "Util" to Pair("dev.frozenmilk.dairy", "Util")
    )

    // Explicit Panels (FullPanels) plugin artifacts
    private val panelsArtifacts = listOf(
        "panels", "battery", "fullpanels", "capture", "configurables", "docs",
        "field", "gamepad", "limelightproxy", "opmodecontrol", "telemetry",
        "themes", "utils", "pinger", "graph", "lights", "camerastream"
    )

    // NextFTC modules
    private val nextFTCModules = listOf("core", "ftc", "pedro", "roadrunner")

    val LIBRARIES: Map<String, LibrarySource> by lazy {
        val dairyRepos = listOf(
            "https://repo.dairy.foundation/releases",
            "https://repo.dairy.foundation/snapshots"
        )
        val map = mutableMapOf<String, LibrarySource>()

        map["Road Runner Core"] = LibrarySource(
            group = "com.github.acmerobotics",
            artifact = "road-runner",
            repoUrl = "https://jitpack.io",
            repositories = listOf("https://jitpack.io"),
            description = "Motion planning library for FTC robots with advanced trajectory generation and following. Docs: https://rr.brott.dev/docs/v1-0/installation/"
        )

        map["FTC Dashboard"] = LibrarySource(
            group = "com.github.acmerobotics",
            artifact = "ftc-dashboard",
            repoUrl = "https://jitpack.io",
            repositories = listOf("https://jitpack.io"),
            description = "Real-time debugging and telemetry tool with graphing. Docs: https://acmerobotics.github.io/ftc-dashboard/"
        )

        map["SlothDash"] = LibrarySource(
            group = "com.acmerobotics.slothboard",
            artifact = "dashboard",
            repoUrl = "https://repo.dairy.foundation/releases",
            repositories = dairyRepos,
            description = "Sloth-compatible dashboard (replaces FTC Dashboard when using Sloth from Dairy Suite). Docs: https://docs.dairy.foundation/"
        )

        map["FTCLib Core"] = LibrarySource(
            group = "com.github.FTCLib",
            artifact = "FTCLib",
            repoUrl = "https://jitpack.io",
            repositories = listOf("https://jitpack.io"),
            description = "Commands, subsystems, kinematics, and control systems. Docs: https://ftclib.org/"
        )

        map["Dairy Suite"] = LibrarySource(
            group = "dev.frozenmilk.dairy",
            artifact = "Core",
            repoUrl = "https://repo.dairy.foundation/releases",
            repositories = dairyRepos,
            subArtifactCoordinates = listOf(
                "dev.frozenmilk.dairy:Core",
                "dev.frozenmilk.mercurial:Mercurial",
                "dev.frozenmilk.dairy:Pasteurized",
                "dev.frozenmilk:Sinister",
                "dev.frozenmilk.sinister:Sloth",
                "dev.frozenmilk.dairy:Util",
                "com.acmerobotics.slothboard:dashboard"
            ),
            description = "Dairy Foundation suite (Core, Mercurial, Pasteurized, Sinister, Sloth, Util, Sloth Dash). Docs: https://docs.dairy.foundation/introduction",
            category = "DAIRY"
        )

        map["NextFTC Suite"] = LibrarySource(
            group = "dev.nextftc",
            artifact = "ftc",
            repoUrl = "https://repo1.maven.org/maven2",
            repositories = emptyList(),
            subArtifactCoordinates = listOf(
                "dev.nextftc:ftc",
                "dev.nextftc:hardware",
                "dev.nextftc:control",
                "dev.nextftc:bindings",
                "dev.nextftc.extensions:pedro",
                "dev.nextftc.extensions:roadrunner",
                "dev.nextftc.extensions:fateweaver"
            ),
            description = "NextFTC core + extensions (hardware, control, bindings, Pedro, RR, Fateweaver). Docs: https://nextftc.dev/",
            category = "NEXTFTC"
        )

        map["Pedro Pathing"] = LibrarySource(
            group = "com.pedropathing",
            artifact = "ftc",
            repoUrl = "https://jitpack.io",
            repositories = listOf("https://jitpack.io"),
            subArtifacts = listOf("ftc", "telemetry"),
            description = "Pure pursuit & path planning. Installs FTC + telemetry modules. Docs: https://pedropathing.com/"
        )

        map["SolversLib Core"] = LibrarySource(
            group = "org.solverslib",
            artifact = "core",
            repoUrl = "https://repo.dairy.foundation/releases",
            repositories = dairyRepos,
            description = "Control theory utilities: PID, feedforward, profiling. Docs: https://docs.seattlesolvers.com/"
        )

        map["SolversLib Pedro Pathing"] = LibrarySource(
            group = "org.solverslib",
            artifact = "pedroPathing",
            repoUrl = "https://repo.dairy.foundation/releases",
            repositories = dairyRepos,
            description = "SolversLib–Pedro Pathing integration layer. Docs: https://solverslib.org/pedro"
        )

        map["Psi Kit"] = LibrarySource(
            group = "org.psilynx",
            artifact = "psikit",
            repoUrl = "https://repo.dairy.foundation/releases",
            repositories = dairyRepos,
            description = "PsiKit by PsiLynx. an Advantage Scope logging framework Docs: https://psilynx.github.io/PsiKit/#/"
        )

        map["Panels Library"] = LibrarySource(
            group = "com.bylazar",
            artifact = "fullpanels",
            repoUrl = "https://mymaven.bylazar.com/releases",
            repositories = listOf("https://mymaven.bylazar.com/releases"),
            subArtifacts = listOf(
                "fullpanels",
                "battery",
                "camerastream",
                "capture",
                "configurables",
                "field",
                "gamepad",
                "graph",
                "lights",
                "limelightproxy",
                "opmodecontrol",
                "pinger",
                "telemetry",
                "themes",
                "utils"
            ),
            description = "FTC Panels suite by byLazar. Install the full bundle or select individual modules (Battery, Camera Stream, Capture, Configurables, Field, Gamepad, Graph, Lights, Limelight Proxy, OpMode Control, Pinger, Telemetry, Themes, Utils). Docs: https://panels.bylazar.com/docs/com.bylazar.docs/",
            category = "PANELS"
        )

        map["FateWeaver"] = LibrarySource(
            group = "gay.zharel.fateweaver",
            artifact = "ftc",
            repoUrl = "https://repo1.maven.org/maven2",
            repositories = emptyList(),
            description = "FateWeaver FTC utilities. Hosted on Maven Central. Usage: implementation(\"gay.zharel.fateweaver:ftc:<version>\"). Docs: https://github.com/HermesFTC/FateWeaver"
        )

        map["Hermes"] = LibrarySource(
            group = "gay.zharel.hermes",
            artifact = "ftc",
            repoUrl = "https://repo1.maven.org/maven2",
            repositories = emptyList(),
            description = "Hermes — motion planning library fork of Roadrunner. Hosted on Maven Central. Usage: implementation(\"gay.zharel.hermes:ftc:<version>\"). Docs: https://hermes.zharel.gay/"
        )

        map["State Factory"] = LibrarySource(
            group = "com.github.StateFactory-Dev",
            artifact = "StateFactory",
            repoUrl = "https://jitpack.io",
            repositories = listOf("https://jitpack.io"),
            description = "Finite state machine framework for FTC. Hosted on JitPack. Usage: implementation(\"com.github.StateFactory-Dev:StateFactory:<version>\"). Docs: https://state-factory.gitbook.io/state-factory"
        )

        map["Koala Log"] = LibrarySource(
            group = "com.github.Koala-Log",
            artifact = "Koala-Log",
            repoUrl = "https://jitpack.io",
            repositories = listOf("https://jitpack.io"),
            description = "Lightweight logging for FTC with structured output. Hosted on JitPack. Usage: implementation(\"com.github.Koala-Log:Koala-Log:<version>\"). Docs: https://github.com/Koala-Log/Koala-Log/wiki/1.-How-to-add-to-project"
        )

        println("Total libraries loaded (aggregated suites applied): ${map.size}")
        map
    }
}
