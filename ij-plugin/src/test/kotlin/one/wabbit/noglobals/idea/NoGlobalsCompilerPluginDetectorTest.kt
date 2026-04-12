// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.noglobals.idea

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import one.wabbit.ijplugin.common.CompilerPluginScan as NoGlobalsCompilerPluginScan
import one.wabbit.ijplugin.common.GradleBuildFileMatch as NoGlobalsGradleBuildFileMatch
import one.wabbit.ijplugin.common.IdeSupportActivationState as NoGlobalsIdeSupportActivationState

class NoGlobalsCompilerPluginDetectorTest {
    @Test
    fun detectsNoGlobalsCompilerPluginInMavenLocalPath() {
        assertTrue(
            NoGlobalsCompilerPluginDetector.isCompilerPluginPath(
                "/Users/example/.m2/repository/one/wabbit/kotlin-no-globals-plugin/0.0.1/kotlin-no-globals-plugin-0.0.1.jar"
            )
        )
    }

    @Test
    fun ignoresUnrelatedCompilerPluginPaths() {
        assertFalse(
            NoGlobalsCompilerPluginDetector.isCompilerPluginPath(
                "/workspace/kotlin-acyclic-plugin/build/libs/kotlin-acyclic-plugin-0.0.1.jar"
            )
        )
    }

    @Test
    fun detectsNoGlobalsGradlePluginIdInBuildScript() {
        assertTrue(
            NoGlobalsCompilerPluginDetector.isDirectGradlePluginReference(
                """
                plugins {
                    id("one.wabbit.no-globals")
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun detectsNoGlobalsGradlePluginIdInSingleLinePluginsBlock() {
        assertTrue(
            NoGlobalsCompilerPluginDetector.isDirectGradlePluginReference(
                """plugins { id("one.wabbit.no-globals") }"""
            )
        )
    }

    @Test
    fun detectsNoGlobalsGradlePluginManagerApply() {
        assertTrue(
            NoGlobalsCompilerPluginDetector.isDirectGradlePluginReference(
                """pluginManager.apply("one.wabbit.no-globals")"""
            )
        )
    }

    @Test
    fun versionCatalogPluginIdDoesNotCountAsDirectGradleReference() {
        assertFalse(
            NoGlobalsCompilerPluginDetector.isDirectGradlePluginReference(
                """
                [plugins]
                no-globals = { id = "one.wabbit.no-globals", version = "0.0.1" }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun unusedLegacyArtifactCoordinateDoesNotCountAsGradleReference() {
        assertFalse(
            NoGlobalsCompilerPluginDetector.isDirectGradlePluginReference(
                """
                dependencies {
                    implementation("one.wabbit:kotlin-no-globals-gradle-plugin:0.0.1")
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun commentedPluginIdDoesNotCountAsGradleReference() {
        assertFalse(
            NoGlobalsCompilerPluginDetector.isDirectGradlePluginReference(
                """
                // id("one.wabbit.no-globals")
                plugins {
                    kotlin("jvm")
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun multilineStringContainingPluginSyntaxDoesNotCountAsGradleReference() {
        assertFalse(
            NoGlobalsCompilerPluginDetector.isDirectGradlePluginReference(
                """
                val docs = ${"\"\"\""}
                    id("one.wabbit.no-globals")
                ${"\"\"\""}
                plugins {
                    kotlin("jvm")
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun pluginIdWithApplyFalseDoesNotCountAsGradleReference() {
        assertFalse(
            NoGlobalsCompilerPluginDetector.isDirectGradlePluginReference(
                """
                plugins {
                    id("one.wabbit.no-globals") apply false
                }
                """
                    .trimIndent()
            )
        )
    }

    @Test
    fun applyFalseDoesNotHideLaterSameLineApplication() {
        assertTrue(
            NoGlobalsCompilerPluginDetector.isDirectGradlePluginReference(
                """plugins { id("one.wabbit.no-globals") apply false; id("one.wabbit.no-globals") }"""
            )
        )
    }

    @Test
    fun matchingGradleBuildFilesFindsPluginReferencesAndSkipsExcludedDirectorySubtrees() {
        val projectRoot = Files.createTempDirectory("no-globals-idea-detector-test")
        projectRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("one.wabbit.no-globals")
                }
                """
                    .trimIndent()
            )
        projectRoot.resolve("gradle").createDirectories()
        projectRoot
            .resolve("gradle/libs.versions.toml")
            .writeText(
                """
                [plugins]
                no-globals = { id = "one.wabbit.no-globals", version = "0.0.1" }
                """
                    .trimIndent()
            )
        listOf(".git", ".gradle", ".idea", "build", "out").forEach { excludedDir ->
            projectRoot.resolve("$excludedDir/generated").createDirectories()
            projectRoot
                .resolve("$excludedDir/generated/build.gradle.kts")
                .writeText(
                    """
                    plugins {
                        id("one.wabbit.no-globals")
                    }
                    """
                        .trimIndent()
                )
        }

        val matches = NoGlobalsCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(listOf("build.gradle.kts"), matches)
    }

    @Test
    fun matchingGradleBuildFilesUsesVersionCatalogAliasesOnlyWhenAppliedInBuildScript() {
        val projectRoot = Files.createTempDirectory("no-globals-idea-detector-alias-test")
        projectRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins { alias(libs.plugins.no.globals) }
                """
                    .trimIndent()
            )
        projectRoot.resolve("gradle").createDirectories()
        projectRoot
            .resolve("gradle/libs.versions.toml")
            .writeText(
                """
                [plugins]
                no-globals = { id = "one.wabbit.no-globals", version = "0.0.1" }
                """
                    .trimIndent()
            )

        val matches = NoGlobalsCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(listOf("build.gradle.kts"), matches)
    }

    @Test
    fun matchingGradleBuildFileMatchesRetainOriginatingRootPath() {
        val projectRoot = Files.createTempDirectory("no-globals-idea-detector-root-path-test")
        val appRoot = projectRoot.resolve("app")
        appRoot.createDirectories()
        appRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("one.wabbit.no-globals")
                }
                """
                    .trimIndent()
            )

        val matches =
            NoGlobalsCompilerPluginDetector.matchingGradleBuildFileMatches(listOf(appRoot))

        assertEquals(
            listOf(
                NoGlobalsGradleBuildFileMatch(
                    relativePath = "build.gradle.kts",
                    rootPath = appRoot.toAbsolutePath().normalize().toString().replace('\\', '/'),
                )
            ),
            matches,
        )
    }

    @Test
    fun matchingGradleBuildFilesDoesNotLeakVersionCatalogAliasesAcrossSelectedRoots() {
        val projectRoot = Files.createTempDirectory("no-globals-idea-detector-root-set-alias-test")
        val appRoot = projectRoot.resolve("app")
        appRoot.createDirectories()
        appRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    alias(libs.plugins.no.globals)
                }
                """
                    .trimIndent()
            )
        projectRoot.resolve("gradle").createDirectories()
        projectRoot
            .resolve("gradle/libs.versions.toml")
            .writeText(
                """
                [plugins]
                no-globals = { id = "one.wabbit.no-globals", version = "0.0.1" }
                """
                    .trimIndent()
            )

        val matches =
            NoGlobalsCompilerPluginDetector.matchingGradleBuildFiles(listOf(projectRoot, appRoot))

        assertEquals(emptyList(), matches)
    }

    @Test
    fun matchingGradleBuildFilesIgnoresVersionCatalogAliasesThatAreNotApplied() {
        val projectRoot = Files.createTempDirectory("no-globals-idea-detector-catalog-only-test")
        projectRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    kotlin("jvm")
                }
                """
                    .trimIndent()
            )
        projectRoot.resolve("gradle").createDirectories()
        projectRoot
            .resolve("gradle/libs.versions.toml")
            .writeText(
                """
                [plugins]
                no-globals = { id = "one.wabbit.no-globals", version = "0.0.1" }
                """
                    .trimIndent()
            )

        val matches = NoGlobalsCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(emptyList(), matches)
    }

    @Test
    fun matchingGradleBuildFilesIgnoresNestedFixtureBuildFiles() {
        val projectRoot = Files.createTempDirectory("no-globals-idea-detector-fixture-test")
        projectRoot.resolve("docs/examples").createDirectories()
        projectRoot
            .resolve("docs/examples/build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("one.wabbit.no-globals")
                }
                """
                    .trimIndent()
            )

        val matches = NoGlobalsCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(emptyList(), matches)
    }

    @Test
    fun matchingGradleBuildFilesIgnoresSettingsScriptsThatOnlyMentionPluginId() {
        val projectRoot = Files.createTempDirectory("no-globals-idea-detector-settings-test")
        projectRoot
            .resolve("settings.gradle.kts")
            .writeText(
                """
                pluginManagement {
                    plugins {
                        id("one.wabbit.no-globals") version "0.0.1"
                    }
                }
                """
                    .trimIndent()
            )
        projectRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    kotlin("jvm")
                }
                """
                    .trimIndent()
            )

        val matches = NoGlobalsCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(emptyList(), matches)
    }

    @Test
    fun matchingGradleBuildFilesDoesNotRejectProjectsRootedUnderBuildLikePathSegments() {
        val sandboxRoot = Files.createTempDirectory("no-globals-idea-detector-root")
        val projectRoot = sandboxRoot.resolve("build/demo")
        projectRoot.createDirectories()
        projectRoot
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("one.wabbit.no-globals")
                }
                """
                    .trimIndent()
            )

        val matches = NoGlobalsCompilerPluginDetector.matchingGradleBuildFiles(projectRoot)

        assertEquals(listOf("build.gradle.kts"), matches)
    }

    @Test
    fun gradleImportRequiredMessageUsesGradlePluginName() {
        val message =
            NoGlobalsIdeSupportCoordinator.buildGradleImportRequiredMessage(
                scan =
                    NoGlobalsCompilerPluginScan(
                        projectLevelMatch = null,
                        moduleMatches = emptyList(),
                        gradleBuildFiles = listOf("build.gradle.kts"),
                    ),
                activationState = NoGlobalsIdeSupportActivationState.ALREADY_ENABLED,
                gradleImportRequested = false,
            )

        assertTrue(message.contains("kotlin-no-globals Gradle plugin"))
    }

    @Test
    fun enabledMessageTreatsRequiredAndRequestedGradleImportAsSeparateStates() {
        val message =
            NoGlobalsIdeSupportCoordinator.buildEnabledMessage(
                scan =
                    NoGlobalsCompilerPluginScan(
                        projectLevelMatch = null,
                        moduleMatches = emptyList(),
                        gradleBuildFiles = listOf("build.gradle.kts"),
                    ),
                registryUpdated = false,
                gradleImportRequired = true,
                gradleImportRequested = false,
            )

        assertTrue(message.contains("kotlin-no-globals Gradle plugin"))
        assertTrue(message.contains("Reimport the Gradle project."))
        assertFalse(message.contains("Requested a Gradle import"))
    }

    @Test
    fun coordinatorUsesMatchedGradleImportPathsBeforeProjectBasePathFallback() {
        val paths =
            NoGlobalsIdeSupportCoordinator.gradleImportPaths(
                scan =
                    NoGlobalsCompilerPluginScan(
                        projectLevelMatch = null,
                        moduleMatches = emptyList(),
                        gradleBuildFiles = listOf("app/build.gradle.kts"),
                        gradleImportPaths = listOf("/repo/app"),
                    ),
                projectBasePath = "/repo",
            )

        assertEquals(listOf("/repo/app"), paths)
    }
}
