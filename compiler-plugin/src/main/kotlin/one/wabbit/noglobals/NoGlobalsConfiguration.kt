package one.wabbit.noglobals

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal data class NoGlobalsConfiguration(
    val enabled: Boolean = false,
    val blacklistedTypeNames: List<String> = emptyList(),
) {
    val blacklistedTypeClassIds: List<ClassId> =
        (DEFAULT_BLACKLISTED_TYPE_FQ_NAMES + blacklistedTypeNames)
            .distinct()
            .map(::classIdForTopLevelType)
}

internal val DEFAULT_BLACKLISTED_TYPE_FQ_NAMES: List<String> =
    listOf(
        "kotlin.collections.MutableList",
        "kotlin.collections.MutableSet",
        "kotlin.collections.MutableMap",
        "kotlin.collections.MutableCollection",
        "java.util.concurrent.atomic.AtomicInteger",
        "java.util.concurrent.atomic.AtomicLong",
        "java.util.concurrent.atomic.AtomicBoolean",
        "java.util.concurrent.atomic.AtomicReference",
    )

internal const val BLACKLISTED_TYPE_OPTION_NAME: String = "blacklistedType"

private fun classIdForTopLevelType(fqName: String): ClassId = ClassId.topLevel(FqName(fqName))

internal fun CompilerConfiguration.addBlacklistedType(typeName: String) {
    val existing = get(NoGlobalsConfigurationKeys.BLACKLISTED_TYPES) ?: emptyList()
    put(NoGlobalsConfigurationKeys.BLACKLISTED_TYPES, existing + typeName)
}

internal object NoGlobalsConfigurationKeys {
    val ENABLED: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create("no-globals enabled")
    val BLACKLISTED_TYPES: CompilerConfigurationKey<List<String>> =
        CompilerConfigurationKey.create("no-globals blacklisted types")
}

internal fun CompilerConfiguration.toNoGlobalsConfiguration(): NoGlobalsConfiguration =
    NoGlobalsConfiguration(
        enabled = get(NoGlobalsConfigurationKeys.ENABLED) ?: NoGlobalsConfiguration().enabled,
        blacklistedTypeNames = get(NoGlobalsConfigurationKeys.BLACKLISTED_TYPES) ?: emptyList(),
    )
