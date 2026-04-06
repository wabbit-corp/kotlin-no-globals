package one.wabbit.noglobals

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal data class BlacklistedTypePattern(
    val configuredName: String,
    val candidateClassIds: List<ClassId>,
)

internal data class NoGlobalsConfiguration(
    val enabled: Boolean = false,
    val blacklistedTypeNames: List<String> = emptyList(),
) {
    val blacklistedTypes: List<BlacklistedTypePattern> =
        (DEFAULT_BLACKLISTED_TYPE_FQ_NAMES + blacklistedTypeNames)
            .distinct()
            .map(::blacklistedTypePattern)
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
        "java.util.concurrent.atomic.AtomicMarkableReference",
        "java.util.concurrent.atomic.AtomicStampedReference",
        "java.util.concurrent.atomic.AtomicIntegerArray",
        "java.util.concurrent.atomic.AtomicLongArray",
        "java.util.concurrent.atomic.AtomicReferenceArray",
        "java.util.concurrent.atomic.LongAdder",
        "java.util.concurrent.atomic.LongAccumulator",
        "java.util.concurrent.atomic.DoubleAdder",
        "java.util.concurrent.atomic.DoubleAccumulator",
        "java.util.concurrent.locks.ReentrantLock",
        "java.util.concurrent.locks.ReentrantReadWriteLock",
        "java.util.concurrent.Semaphore",
        "java.util.concurrent.CountDownLatch",
        "java.util.concurrent.CyclicBarrier",
        "java.util.concurrent.Phaser",
        "kotlin.concurrent.atomics.AtomicInt",
        "kotlin.concurrent.atomics.AtomicLong",
        "kotlin.concurrent.atomics.AtomicBoolean",
        "kotlin.concurrent.atomics.AtomicReference",
        "kotlin.concurrent.atomics.AtomicIntArray",
        "kotlin.concurrent.atomics.AtomicLongArray",
        "kotlin.concurrent.atomics.AtomicArray",
        "kotlinx.coroutines.flow.MutableStateFlow",
        "kotlinx.coroutines.flow.MutableSharedFlow",
        "kotlinx.coroutines.sync.Mutex",
        "kotlinx.coroutines.sync.Semaphore",
        "kotlinx.coroutines.channels.Channel",
        "kotlinx.atomicfu.AtomicInt",
        "kotlinx.atomicfu.AtomicLong",
        "kotlinx.atomicfu.AtomicBoolean",
        "kotlinx.atomicfu.AtomicRef",
        "kotlin.text.StringBuilder",
        "java.lang.StringBuilder",
        "java.lang.StringBuffer",
    )

internal const val BLACKLISTED_TYPE_OPTION_NAME: String = "blacklistedType"

private fun blacklistedTypePattern(typeName: String): BlacklistedTypePattern =
    BlacklistedTypePattern(
        configuredName = typeName,
        candidateClassIds = candidateClassIdsForConfiguredTypeName(typeName),
    )

private fun candidateClassIdsForConfiguredTypeName(typeName: String): List<ClassId> {
    val segments = typeName.split('.').filter(String::isNotBlank)
    if (segments.isEmpty()) {
        return emptyList()
    }

    val preferredSplitIndex =
        segments.indexOfFirst { segment -> segment.firstOrNull()?.isUpperCase() == true }
            .takeIf { index -> index >= 0 }

    return buildList {
        preferredSplitIndex?.let { add(classIdForSplit(segments, it)) }
        for (packageSegmentCount in 0 until segments.size) {
            if (packageSegmentCount == preferredSplitIndex) {
                continue
            }
            add(classIdForSplit(segments, packageSegmentCount))
        }
    }.distinct()
}

private fun classIdForSplit(
    segments: List<String>,
    packageSegmentCount: Int,
): ClassId {
    val packageName = segments.take(packageSegmentCount).joinToString(".")
    val relativeClassName = segments.drop(packageSegmentCount).joinToString(".")
    if (!relativeClassName.contains('.')) {
        val topLevelFqName =
            listOf(packageName, relativeClassName)
                .filter(String::isNotEmpty)
                .joinToString(".")
        return ClassId.topLevel(FqName(topLevelFqName))
    }
    return ClassId(
        packageFqName = FqName(packageName),
        relativeClassName = FqName(relativeClassName),
        isLocal = false,
    )
}

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
