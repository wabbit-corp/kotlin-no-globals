package one.wabbit.noglobals.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.assertContains
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NoGlobalsGradlePluginTest {
    @Test
    fun `enabled defaults to true`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(NoGlobalsGradlePlugin::class.java)

        val extension = assertNotNull(project.extensions.findByType(NoGlobalsExtension::class.java))
        val options = noGlobalsSubpluginOptions(project).get()

        assertEquals(true, extension.enabled.get())
        assertEquals(1, options.size)
        assertEquals("enabled", options.single().key)
        assertEquals("true", options.single().value)
    }

    @Test
    fun `enabled false is passed through to the compiler plugin option`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(NoGlobalsGradlePlugin::class.java)

        val extension = assertNotNull(project.extensions.findByType(NoGlobalsExtension::class.java))
        extension.enabled.set(false)
        val options = noGlobalsSubpluginOptions(project).get()

        assertEquals(1, options.size)
        assertEquals("enabled", options.single().key)
        assertEquals("false", options.single().value)
    }

    @Test
    fun `custom blacklist entries are passed through alongside enabled`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(NoGlobalsGradlePlugin::class.java)

        val extension = assertNotNull(project.extensions.findByType(NoGlobalsExtension::class.java))
        extension.blacklistedTypes.add("sample.MutableBox")
        extension.blacklistedTypes.add("sample.MutableCache")
        val options = noGlobalsSubpluginOptions(project).get()

        assertEquals(
            listOf("enabled" to "true", "blacklistedType" to "sample.MutableBox", "blacklistedType" to "sample.MutableCache"),
            options.map { it.key to it.value },
        )
    }

    @Test
    fun `plugin auto adds marker annotation dependency for kotlin jvm`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(NoGlobalsGradlePlugin::class.java)
        project.plugins.apply("org.jetbrains.kotlin.jvm")

        val implementation = project.configurations.getByName("implementation")
        val dependencyCoordinates = implementation.dependencies.map { dependency ->
            "${dependency.group}:${dependency.name}:${dependency.version}"
        }

        assertContains(
            dependencyCoordinates,
            "one.wabbit:kotlin-no-globals:$NO_GLOBALS_GRADLE_PLUGIN_VERSION",
        )
    }

    @Test
    fun `plugin auto adds marker annotation dependency for kotlin multiplatform`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply(NoGlobalsGradlePlugin::class.java)
        project.plugins.apply("org.jetbrains.kotlin.multiplatform")

        val commonMainImplementation = project.configurations.getByName("commonMainImplementation")
        val dependencyCoordinates = commonMainImplementation.dependencies.map { dependency ->
            "${dependency.group}:${dependency.name}:${dependency.version}"
        }

        assertContains(
            dependencyCoordinates,
            "one.wabbit:kotlin-no-globals:$NO_GLOBALS_GRADLE_PLUGIN_VERSION",
        )
    }

    @Test
    fun `plugin does not duplicate marker annotation dependency when already present`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.dependencies.add(
            "implementation",
            "one.wabbit:kotlin-no-globals:$NO_GLOBALS_GRADLE_PLUGIN_VERSION",
        )

        project.plugins.apply(NoGlobalsGradlePlugin::class.java)

        val implementation = project.configurations.getByName("implementation")
        val dependencyCount =
            implementation.dependencies.count { dependency ->
                dependency.group == "one.wabbit" &&
                    dependency.name == "kotlin-no-globals" &&
                    dependency.version == NO_GLOBALS_GRADLE_PLUGIN_VERSION
            }

        assertEquals(1, dependencyCount)
    }
}
