// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.noglobals.idea

import one.wabbit.ijplugin.common.CompilerPluginIdeSupportDescriptor
import one.wabbit.ijplugin.common.ConfiguredCompilerPluginDetectorSupport
import one.wabbit.ijplugin.common.ConfiguredCompilerPluginIdeSupport

internal const val NO_GLOBALS_COMPILER_PLUGIN_MARKER = "kotlin-no-globals-plugin"
internal const val NO_GLOBALS_GRADLE_PLUGIN_ID = "one.wabbit.no-globals"

internal val NO_GLOBALS_PLUGIN_SUPPORT =
    ConfiguredCompilerPluginIdeSupport(
        descriptor =
            CompilerPluginIdeSupportDescriptor(
                loggerCategory = NoGlobalsIdeSupportCoordinator::class.java,
                notificationGroupId = "NoGlobalsIdeSupport",
                supportDisplayName = "No-Globals",
                supportDisplayNameLowercase = "no-globals",
                compilerPluginMarker = NO_GLOBALS_COMPILER_PLUGIN_MARKER,
                compilerPluginDisplayName = "kotlin-no-globals-plugin",
                gradlePluginId = NO_GLOBALS_GRADLE_PLUGIN_ID,
                externalPluginDisplayName = "kotlin-no-globals",
                analysisRestartReason = "No-Globals IDE support activation",
                enablementLogMessage = { project ->
                    "Temporarily enabling non-bundled K2 compiler plugins for project ${project.name}"
                },
                gradleImportDetectedName = "kotlin-no-globals Gradle plugin",
            )
    )

internal object NoGlobalsCompilerPluginDetector :
    ConfiguredCompilerPluginDetectorSupport(NO_GLOBALS_PLUGIN_SUPPORT)
