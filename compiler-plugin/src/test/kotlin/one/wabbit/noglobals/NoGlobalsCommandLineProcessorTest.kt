// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.noglobals

import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.config.CompilerConfiguration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

@Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
class NoGlobalsCommandLineProcessorTest {
    private val processor = NoGlobalsCommandLineProcessor()
    private val blacklistedTypeOption =
        processor.pluginOptions.single { option -> option.optionName == BLACKLISTED_TYPE_OPTION_NAME }

    @Test
    fun `malformed blacklisted type entries fail loudly`() {
        val exception =
            assertFailsWith<CliOptionProcessingException> {
                processor.processOption(
                    blacklistedTypeOption,
                    "sample..MutableBox",
                    CompilerConfiguration(),
                )
            }

        assertContains(exception.message.orEmpty(), BLACKLISTED_TYPE_OPTION_NAME)
    }
}
