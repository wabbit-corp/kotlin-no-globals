@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package one.wabbit.noglobals

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
/**
 * Compiler-plugin registrar for `kotlin-no-globals`.
 *
 * This is the K2 entry point used by the Kotlin compiler to install the FIR-based global mutable
 * state checkers. In normal builds you do not reference this class directly; the Gradle plugin
 * resolves and wires the compiler plugin artifact automatically.
 *
 * The registrar is public because Kotlin compiler plugins are discovered through compiler SPI, not
 * because it is intended as a general-purpose user API.
 */
class NoGlobalsCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = NO_GLOBALS_PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val pluginConfiguration = configuration.toNoGlobalsConfiguration()
        if (!pluginConfiguration.enabled) {
            return
        }
        FirExtensionRegistrarAdapter.registerExtension(NoGlobalsFirExtensionRegistrar(pluginConfiguration))
    }
}

private class NoGlobalsFirExtensionRegistrar(
    private val pluginConfiguration: NoGlobalsConfiguration,
) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +{ session: FirSession ->
            NoGlobalsCheckersExtension(session, pluginConfiguration)
        }
    }
}
