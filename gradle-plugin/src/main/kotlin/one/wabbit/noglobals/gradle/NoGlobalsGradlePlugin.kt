package one.wabbit.noglobals.gradle

import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.model.ObjectFactory
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import javax.inject.Inject

private const val NO_GLOBALS_COMPILER_PLUGIN_ID = "one.wabbit.no-globals"
private const val NO_GLOBALS_COMPILER_PLUGIN_GROUP = "one.wabbit"
private const val NO_GLOBALS_COMPILER_PLUGIN_ARTIFACT = "kotlin-no-globals-plugin"

abstract class NoGlobalsExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
        val blacklistedTypes: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    }

class NoGlobalsGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create("noGlobals", NoGlobalsExtension::class.java)
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
        noGlobalsSubpluginOptions(kotlinCompilation.target.project)

    override fun getCompilerPluginId(): String = NO_GLOBALS_COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = NO_GLOBALS_COMPILER_PLUGIN_GROUP,
            artifactId = NO_GLOBALS_COMPILER_PLUGIN_ARTIFACT,
            version = NO_GLOBALS_GRADLE_PLUGIN_VERSION,
        )
}

internal fun noGlobalsSubpluginOptions(project: Project): Provider<List<SubpluginOption>> {
    val extension = project.extensions.getByType(NoGlobalsExtension::class.java)
    return project.provider {
        buildList {
            add(
                SubpluginOption(
                    key = "enabled",
                    value = extension.enabled.get().toString(),
                ),
            )
            extension.blacklistedTypes.get().forEach { blacklistedType ->
                add(
                    SubpluginOption(
                        key = "blacklistedType",
                        value = blacklistedType,
                    ),
                )
            }
        }
    }
}
