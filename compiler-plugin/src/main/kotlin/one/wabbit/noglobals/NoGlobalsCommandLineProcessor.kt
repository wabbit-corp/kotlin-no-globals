@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package one.wabbit.noglobals

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

class NoGlobalsCommandLineProcessor : CommandLineProcessor {
    private val enabledOption =
        CliOption(
            optionName = "enabled",
            valueDescription = "<true|false>",
            description = "Enables global mutable state checking.",
            required = false,
            allowMultipleOccurrences = false,
        )
    private val blacklistedTypeOption =
        CliOption(
            optionName = BLACKLISTED_TYPE_OPTION_NAME,
            valueDescription = "<fq-name>",
            description = "Adds a mutable-state carrier type to the blacklist.",
            required = false,
            allowMultipleOccurrences = true,
        )

    override val pluginId: String = NO_GLOBALS_PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(enabledOption, blacklistedTypeOption)

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            enabledOption.optionName ->
                configuration.put(
                    NoGlobalsConfigurationKeys.ENABLED,
                    parseEnabled(value),
                )

            blacklistedTypeOption.optionName ->
                configuration.addBlacklistedType(parseBlacklistedType(value))

            else -> throw CliOptionProcessingException("Unknown option ${option.optionName}")
        }
    }

    private fun parseEnabled(value: String): Boolean =
        when (value) {
            "true" -> true
            "false" -> false
            else -> throw CliOptionProcessingException("Invalid value '$value' for option 'enabled'")
        }

    private fun parseBlacklistedType(value: String): String =
        value.trim().takeIf(String::isNotEmpty)
            ?: throw CliOptionProcessingException("Invalid value '$value' for option '$BLACKLISTED_TYPE_OPTION_NAME'")
}
