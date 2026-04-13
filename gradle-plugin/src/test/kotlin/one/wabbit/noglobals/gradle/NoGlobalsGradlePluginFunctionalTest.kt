// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.noglobals.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NoGlobalsGradlePluginFunctionalTest {
    @Test
    fun `plugin rejects top level var in a real Gradle build`() {
        val projectDir = Files.createTempDirectory("no-globals-gradle-functional-test")
        try {
            writeFunctionalBuildFiles(projectDir)
            projectDir.resolve("src/main/kotlin/sample/Test.kt").writeParentAndText(
                """
                package sample

                var counter: Int = 0
                """.trimIndent(),
            )

            val result =
                functionalGradleRunner(projectDir, "compileKotlin", "--stacktrace")
                    .buildAndFail()

            assertContains(result.output, "Global mutable state detected (top-level var)")
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `plugin auto adds marker annotation dependency in a real Gradle build`() {
        val projectDir = Files.createTempDirectory("no-globals-gradle-functional-test")
        try {
            writeFunctionalBuildFiles(projectDir)
            projectDir.resolve("src/main/kotlin/sample/Globals.kt").writeParentAndText(
                """
                package sample

                import one.wabbit.noglobals.RequiresGlobalState

                @RequiresGlobalState
                var counter: Int = 0
                """.trimIndent(),
            )
            projectDir.resolve("src/main/kotlin/sample/Use.kt").writeParentAndText(
                """
                package sample

                import one.wabbit.noglobals.RequiresGlobalState

                @OptIn(RequiresGlobalState::class)
                fun readCounter(): Int = counter
                """.trimIndent(),
            )

            val result =
                functionalGradleRunner(projectDir, "compileKotlin", "--stacktrace")
                    .build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `pluginManagement includeBuild alone does not substitute compiler plugin artifacts when published Wabbit artifacts are unavailable`() {
        val projectDir = Files.createTempDirectory("no-globals-gradle-functional-test")
        try {
            writeFunctionalBuildFiles(
                projectDir,
                includeCompositeBuildForDependencies = false,
                allowPublishedWabbitArtifacts = false,
            )
            projectDir.resolve("src/main/kotlin/sample/Test.kt").writeParentAndText(
                """
                package sample

                var counter: Int = 0
                """.trimIndent(),
            )

            val result =
                functionalGradleRunner(projectDir, "compileKotlin", "--stacktrace")
                    .buildAndFail()

            assertFalse(
                result.output.contains("Global mutable state detected (top-level var)"),
                result.output,
            )
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `native actual object can opt in at declaration without repeatable annotation failure`() {
        val nativeTarget = hostNativeFunctionalTarget() ?: return
        val projectDir = Files.createTempDirectory("no-globals-gradle-functional-test")
        try {
            writeNativeFunctionalBuildFiles(projectDir, nativeTarget)
            projectDir.resolve("src/commonMain/kotlin/sample/Stable.kt").writeParentAndText(
                """
                package sample

                expect object Stable {
                    fun next(): Long
                }
                """.trimIndent(),
            )
            projectDir.resolve("src/${nativeTarget.sourceSetName}/kotlin/sample/Stable.kt").writeParentAndText(
                """
                package sample

                import one.wabbit.noglobals.RequiresGlobalState
                import kotlin.concurrent.atomics.AtomicLong
                import kotlin.concurrent.atomics.ExperimentalAtomicApi

                @OptIn(ExperimentalAtomicApi::class, RequiresGlobalState::class)
                actual object Stable {
                    @RequiresGlobalState
                    private val nextId = AtomicLong(1L)

                    @RequiresGlobalState
                    actual fun next(): Long = nextId.fetchAndAdd(1L)
                }
                """.trimIndent(),
            )

            val result =
                functionalGradleRunner(projectDir, nativeTarget.compileTask, "--stacktrace")
                    .build()

            assertEquals(TaskOutcome.SUCCESS, result.task(":${nativeTarget.compileTask}")?.outcome)
            assertContains(result.output, "BUILD SUCCESSFUL")
        } finally {
            projectDir.toFile().deleteRecursively()
        }
    }
}

private fun writeFunctionalBuildFiles(
    projectDir: Path,
    includeCompositeBuildForDependencies: Boolean = true,
    allowPublishedWabbitArtifacts: Boolean = true,
) {
    val repositoryRoot = repositoryRoot()
    val dependencyIncludeBuild =
        if (includeCompositeBuildForDependencies) {
            """includeBuild("${repositoryRoot.invariantSeparatorsPathString}")"""
        } else {
            ""
        }
    val dependencyResolutionRepositories =
        if (allowPublishedWabbitArtifacts) {
            """
            repositories {
                google()
                mavenCentral()
            }
            """.trimIndent()
        } else {
            """
            repositories {
                google()
                mavenCentral {
                    content {
                        excludeGroup("one.wabbit")
                        excludeGroupByRegex("one\\.wabbit\\..+")
                    }
                }
            }
            """.trimIndent()
        }
    val projectRepositories =
        if (allowPublishedWabbitArtifacts) {
            """
            repositories {
                google()
                mavenCentral()
            }
            """.trimIndent()
        } else {
            """
            repositories {
                google()
                mavenCentral {
                    content {
                        excludeGroup("one.wabbit")
                        excludeGroupByRegex("one\\.wabbit\\..+")
                    }
                }
            }
            """.trimIndent()
        }
    projectDir.resolve("settings.gradle.kts").writeText(
        """
        pluginManagement {
            includeBuild("${repositoryRoot.invariantSeparatorsPathString}")

            repositories {
                gradlePluginPortal()
                google()
                mavenCentral()
            }
        }

        dependencyResolutionManagement {
            $dependencyResolutionRepositories
        }

        $dependencyIncludeBuild

        rootProject.name = "functional-test"
        """.trimIndent(),
    )
    projectDir.resolve("build.gradle.kts").writeText(
        """
        plugins {
            kotlin("jvm") version "${kotlinVersion()}"
            id("one.wabbit.no-globals")
        }

        $projectRepositories

        kotlin {
            jvmToolchain(21)
        }
        """.trimIndent(),
    )
}

private fun writeNativeFunctionalBuildFiles(
    projectDir: Path,
    nativeTarget: FunctionalNativeTarget,
) {
    val repositoryRoot = repositoryRoot()
    projectDir.resolve("settings.gradle.kts").writeText(
        """
        pluginManagement {
            includeBuild("${repositoryRoot.invariantSeparatorsPathString}")

            repositories {
                gradlePluginPortal()
                google()
                mavenCentral()
            }
        }

        dependencyResolutionManagement {
            repositories {
                google()
                mavenCentral()
            }
        }

        includeBuild("${repositoryRoot.invariantSeparatorsPathString}")

        rootProject.name = "functional-test-native"
        """.trimIndent(),
    )
    projectDir.resolve("build.gradle.kts").writeText(
        """
        plugins {
            kotlin("multiplatform") version "${kotlinVersion()}"
            id("one.wabbit.no-globals")
        }

        repositories {
            google()
            mavenCentral()
        }

        kotlin {
            ${nativeTarget.targetDsl}
        }
        """.trimIndent(),
    )
}

private fun Path.writeParentAndText(text: String) {
    parent.createDirectories()
    writeText(text)
}

private fun functionalGradleRunner(projectDir: Path, vararg arguments: String): GradleRunner =
    GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withTestKitDir(projectDir.resolve(".gradle-test-kit").toFile())
        .withArguments(
            listOf(
                "--gradle-user-home",
                projectDir.resolve(".gradle-user-home").invariantSeparatorsPathString,
            ) + arguments.toList()
        )

private fun repositoryRoot(): Path =
    generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { current -> current.parent }
        .firstOrNull { current ->
            current.resolve("settings.gradle.kts").toFile().isFile &&
                current.resolve("compiler-plugin").toFile().isDirectory &&
                current.resolve("gradle-plugin").toFile().isDirectory &&
                current.resolve("library").toFile().isDirectory
        } ?: error("Could not locate kotlin-no-globals repository root from ${System.getProperty("user.dir")}")

private fun kotlinVersion(): String =
    repositoryRoot()
        .resolve("gradle.properties")
        .readLines()
        .first { line -> line.startsWith("defaultKotlinVersion=") }
        .substringAfter('=')

private fun projectVersion(): String =
    repositoryRoot()
        .resolve("gradle.properties")
        .readLines()
        .first { line -> line.startsWith("projectVersion=") }
        .substringAfter('=')

private data class FunctionalNativeTarget(
    val targetDsl: String,
    val sourceSetName: String,
    val compileTask: String,
)

private fun hostNativeFunctionalTarget(): FunctionalNativeTarget? {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    return when {
        osName.contains("mac") && (osArch.contains("arm64") || osArch.contains("aarch64")) ->
            FunctionalNativeTarget("macosArm64()", "macosArm64Main", "compileKotlinMacosArm64")
        osName.contains("mac") ->
            FunctionalNativeTarget("macosX64()", "macosX64Main", "compileKotlinMacosX64")
        osName.contains("linux") && osArch.contains("arm64") ->
            FunctionalNativeTarget("linuxArm64()", "linuxArm64Main", "compileKotlinLinuxArm64")
        osName.contains("linux") ->
            FunctionalNativeTarget("linuxX64()", "linuxX64Main", "compileKotlinLinuxX64")
        osName.contains("windows") ->
            FunctionalNativeTarget("mingwX64()", "mingwX64Main", "compileKotlinMingwX64")
        else -> null
    }
}
