// SPDX-License-Identifier: AGPL-3.0-or-later

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
private const val NO_GLOBALS_ANNOTATION_ARTIFACT = "kotlin-no-globals"

/**
 * Gradle DSL exposed as `noGlobals { ... }`.
 *
 * Use this extension to configure the `kotlin-no-globals` compiler plugin for a module.
 *
 * Typical usage:
 *
 * ```kotlin
 * noGlobals {
 *     enabled.set(true)
 *     blacklistedTypes.add("sample.MutableBox")
 * }
 * ```
 */
abstract class NoGlobalsExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        /**
         * Enables or disables global mutable state checking for the current Gradle project.
         *
         * Default: `true`.
         */
        val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

        /**
         * Additional fully qualified type names that should be treated as mutable-state carriers.
         *
         * These entries extend the built-in blacklist instead of replacing it. Values are passed
         * through to the compiler plugin as repeated `blacklistedType` options.
         *
         * Examples:
         *
         * - `sample.MutableBox`
         * - `sample.Outer.MutableCache`
         */
        val blacklistedTypes: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    }

/**
 * Gradle entry point for `kotlin-no-globals`.
 *
 * Plugin ID: `one.wabbit.no-globals`
 *
 * When applied, this plugin:
 *
 * - registers the `noGlobals {}` DSL
 * - supplies compiler-plugin options to Kotlin compilations
 * - adds the marker annotation dependency for supported Kotlin plugin setups
 *
 * Most users should interact with `kotlin-no-globals` through this Gradle plugin rather than by
 * wiring the compiler plugin manually.
 */
class NoGlobalsGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create("noGlobals", NoGlobalsExtension::class.java)
        configureAnnotationDependency(target)
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
        noGlobalsSubpluginOptions(kotlinCompilation.target.project)

    override fun getCompilerPluginId(): String = NO_GLOBALS_COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(
            groupId = NO_GLOBALS_COMPILER_PLUGIN_GROUP,
            artifactId = NO_GLOBALS_COMPILER_PLUGIN_ARTIFACT,
            version =
                compilerPluginArtifactVersion(
                    baseVersion = NO_GLOBALS_GRADLE_PLUGIN_VERSION,
                    kotlinVersion = currentKotlinGradlePluginVersion(),
                ),
        )
}

private fun configureAnnotationDependency(target: Project) {
    val annotationNotation =
        "$NO_GLOBALS_COMPILER_PLUGIN_GROUP:$NO_GLOBALS_ANNOTATION_ARTIFACT:$NO_GLOBALS_GRADLE_PLUGIN_VERSION"

    target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        addAnnotationDependencyIfMissing(target, "commonMainImplementation", annotationNotation)
    }
    target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        addAnnotationDependencyIfMissing(target, "implementation", annotationNotation)
    }
    target.pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        addAnnotationDependencyIfMissing(target, "implementation", annotationNotation)
    }
    target.pluginManager.withPlugin("org.jetbrains.kotlin.js") {
        addAnnotationDependencyIfMissing(target, "implementation", annotationNotation)
    }
}

private fun addAnnotationDependencyIfMissing(
    target: Project,
    configurationName: String,
    annotationNotation: String,
) {
    val configuration = target.configurations.findByName(configurationName) ?: return
    val alreadyPresent =
        configuration.dependencies.any { dependency ->
            dependency.group == NO_GLOBALS_COMPILER_PLUGIN_GROUP &&
                dependency.name == NO_GLOBALS_ANNOTATION_ARTIFACT &&
                dependency.version == NO_GLOBALS_GRADLE_PLUGIN_VERSION
        }
    if (!alreadyPresent) {
        target.dependencies.add(configurationName, annotationNotation)
    }
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
