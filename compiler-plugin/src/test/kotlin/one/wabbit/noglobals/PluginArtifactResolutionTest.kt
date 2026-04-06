package one.wabbit.noglobals

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PluginArtifactResolutionTest {
    @Test
    fun `resolvePluginArtifact ignores sources and javadoc jars`() {
        val libsDirectory = Files.createTempDirectory("plugin-artifact-resolution-test")
        try {
            libsDirectory.resolve("kotlin-no-globals-plugin-0.0.1-kotlin-2.3.10-sources.jar").createFile()
            libsDirectory.resolve("kotlin-no-globals-plugin-0.0.1-kotlin-2.3.10-javadoc.jar").createFile()
            val runtimeJar = libsDirectory.resolve("kotlin-no-globals-plugin-0.0.1-kotlin-2.3.10.jar").createFile()

            assertEquals(runtimeJar, resolvePluginArtifact(libsDirectory))
        } finally {
            libsDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `resolvePluginArtifact fails when multiple runtime jars are present`() {
        val libsDirectory = Files.createTempDirectory("plugin-artifact-resolution-test")
        try {
            libsDirectory.resolve("kotlin-no-globals-plugin-0.0.1-kotlin-2.3.10.jar").createFile()
            libsDirectory.resolve("kotlin-no-globals-plugin-0.0.2-kotlin-2.3.10.jar").createFile()

            assertFailsWith<IllegalStateException> {
                resolvePluginArtifact(libsDirectory)
            }
        } finally {
            libsDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `locatePluginArtifact prefers explicit system property path`() {
        val repositoryRoot = Files.createTempDirectory("plugin-artifact-resolution-test")
        val explicitJar = repositoryRoot.resolve("custom/kotlin-no-globals-plugin.jar")
        try {
            explicitJar.parent.createDirectories()
            explicitJar.createFile()

            assertEquals(
                explicitJar,
                locatePluginArtifact(
                    explicitPluginJarPath = explicitJar.toString(),
                    repositoryRoot = repositoryRoot,
                ),
            )
        } finally {
            repositoryRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `locatePluginArtifact rejects missing explicit system property path`() {
        val repositoryRoot = Files.createTempDirectory("plugin-artifact-resolution-test")
        val missingJar = repositoryRoot.resolve("missing/kotlin-no-globals-plugin.jar")
        try {
            assertFailsWith<IllegalStateException> {
                locatePluginArtifact(
                    explicitPluginJarPath = missingJar.toString(),
                    repositoryRoot = repositoryRoot,
                )
            }
        } finally {
            repositoryRoot.toFile().deleteRecursively()
        }
    }
}
