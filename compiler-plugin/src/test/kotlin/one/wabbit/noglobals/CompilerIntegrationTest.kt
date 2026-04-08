// SPDX-License-Identifier: LicenseRef-Wabbit-Public-Test-License-1.1

package one.wabbit.noglobals

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

private const val TEST_PLUGIN_ID: String = "one.wabbit.no-globals"

class CompilerIntegrationTest {
    @Test
    fun `top level var requires RequiresGlobalState`() {
        val result =
            compileSnippet(
                """
                package sample

                var counter: Int = 0
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "top-level var")
        assertContains(result.renderedMessages(), "@RequiresGlobalState")
    }

    @Test
    fun `annotated top level var compiles`() {
        val result =
            compileSnippet(
                """
                package sample

                import one.wabbit.noglobals.RequiresGlobalState

                @RequiresGlobalState
                var counter: Int = 0
                """.trimIndent(),
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `const val remains allowed`() {
        val result =
            compileSnippet(
                """
                package sample

                const val answer: Int = 42
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `enabled false disables global mutable state checking`() {
        val result =
            compileSnippet(
                """
                package sample

                var counter: Int = 0
                """.trimIndent(),
                enabled = false,
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `getter annotated top level var is rejected by kotlin opt in rules`() {
        val result =
            compileSnippet(
                """
                package sample

                import one.wabbit.noglobals.RequiresGlobalState

                var counter: Int = 0
                    @RequiresGlobalState get
                """.trimIndent(),
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "cannot be used on getter")
    }

    @Test
    fun `setter annotated top level var is rejected by kotlin opt in rules`() {
        val result =
            compileSnippet(
                """
                package sample

                import one.wabbit.noglobals.RequiresGlobalState

                var counter: Int = 0
                    @RequiresGlobalState set
                """.trimIndent(),
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "Global mutable state detected")
        assertContains(result.renderedMessages(), "not applicable to target 'setter'")
    }

    @Test
    fun `Suppress can silence the global mutable state diagnostic`() {
        val result =
            compileSnippet(
                """
                package sample

                @Suppress("GLOBAL_MUTABLE_STATE_REQUIRES_OPT_IN")
                var counter: Int = 0
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `annotated top level var requires opt in at use site`() {
        val result =
            compileSources(
                mapOf(
                    "sample/Globals.kt" to
                        """
                        package sample

                        import one.wabbit.noglobals.RequiresGlobalState

                        @RequiresGlobalState
                        var counter: Int = 0
                        """.trimIndent(),
                    "sample/Use.kt" to
                        """
                        package sample

                        fun readCounter(): Int = counter
                        """.trimIndent(),
                ),
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "global mutable state")
    }

    @Test
    fun `opt in allows use of annotated global mutable state`() {
        val result =
            compileSources(
                mapOf(
                    "sample/Globals.kt" to
                        """
                        package sample

                        import one.wabbit.noglobals.RequiresGlobalState

                        @RequiresGlobalState
                        var counter: Int = 0
                        """.trimIndent(),
                    "sample/Use.kt" to
                        """
                        package sample

                        import one.wabbit.noglobals.RequiresGlobalState

                        @OptIn(RequiresGlobalState::class)
                        fun readCounter(): Int = counter
                        """.trimIndent(),
                ),
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `vars inside objects require RequiresGlobalState`() {
        val result =
            compileSnippet(
                """
                package sample

                object Globals {
                    var counter: Int = 0
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "object singleton")
    }

    @Test
    fun `vars inside companion objects require RequiresGlobalState`() {
        val result =
            compileSnippet(
                """
                package sample

                class Globals {
                    companion object {
                        var counter: Int = 0
                    }
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "object singleton")
    }

    @Test
    fun `vars inside nested objects are still treated as singleton globals`() {
        val result =
            compileSnippet(
                """
                package sample

                object Outer {
                    object Inner {
                        var counter: Int = 0
                    }
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "object singleton")
    }

    @Test
    fun `vars inside nested companion objects are still treated as singleton globals`() {
        val result =
            compileSnippet(
                """
                package sample

                class Outer {
                    companion object {
                        object Inner {
                            var counter: Int = 0
                        }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "object singleton")
    }

    @Test
    fun `vars inside data objects are treated as singleton globals`() {
        val result =
            compileSnippet(
                """
                package sample

                data object Globals {
                    var counter: Int = 0
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "object singleton")
    }

    @Test
    fun `instance vars in class nested inside object are ignored`() {
        val result =
            compileSnippet(
                """
                package sample

                object Outer {
                    class Inner {
                        var counter: Int = 0
                    }
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `instance vars in class nested inside companion object are ignored`() {
        val result =
            compileSnippet(
                """
                package sample

                class Outer {
                    companion object {
                        class Inner {
                            var counter: Int = 0
                        }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `instance vars are ignored`() {
        val result =
            compileSnippet(
                """
                package sample

                class Box {
                    var counter: Int = 0
                    lateinit var label: String
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `top level delegated vars are rejected`() {
        val result =
            compileSnippet(
                """
                package sample

                import kotlin.properties.Delegates

                var counter: Int by Delegates.observable(0) { _, _, _ -> }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "top-level var")
    }

    @Test
    fun `top level delegated mutable list vals are rejected`() {
        val result =
            compileSnippet(
                """
                package sample

                val users: MutableList<String> by lazy { mutableListOf<String>() }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "top-level val")
        assertContains(result.renderedMessages(), "kotlin.collections.MutableList")
    }

    @Test
    fun `top level mutable list vals with custom getter are ignored`() {
        val result =
            compileSnippet(
                """
                package sample

                val users: MutableList<String>
                    get() = mutableListOf<String>()
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `top level mutable list vals are rejected by default blacklist`() {
        val result =
            compileSnippet(
                """
                package sample

                val users = mutableListOf<String>()
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "top-level val")
        assertContains(result.renderedMessages(), "kotlin.collections.MutableList")
    }

    @Test
    fun `top level atomic integer vals are rejected by default blacklist`() {
        val result =
            compileSnippet(
                """
                package sample

                val counter = java.util.concurrent.atomic.AtomicInteger(0)
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "top-level val")
        assertContains(result.renderedMessages(), "java.util.concurrent.atomic.AtomicInteger")
    }

    @Test
    fun `top level atomic array vals are rejected by default blacklist`() {
        val result =
            compileSnippet(
                """
                package sample

                import java.util.concurrent.atomic.AtomicIntegerArray
                import java.util.concurrent.atomic.AtomicLongArray
                import java.util.concurrent.atomic.AtomicReferenceArray

                val ints = AtomicIntegerArray(1)
                val longs = AtomicLongArray(1)
                val refs = AtomicReferenceArray<String>(1)
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "java.util.concurrent.atomic.AtomicIntegerArray")
        assertContains(result.renderedMessages(), "java.util.concurrent.atomic.AtomicLongArray")
        assertContains(result.renderedMessages(), "java.util.concurrent.atomic.AtomicReferenceArray")
    }

    @Test
    fun `top level concurrent atomic reference and accumulator vals are rejected by default blacklist`() {
        val result =
            compileSnippet(
                """
                package sample

                import java.util.concurrent.atomic.AtomicMarkableReference
                import java.util.concurrent.atomic.AtomicStampedReference
                import java.util.concurrent.atomic.DoubleAccumulator
                import java.util.concurrent.atomic.DoubleAdder
                import java.util.concurrent.atomic.LongAccumulator
                import java.util.concurrent.atomic.LongAdder

                val markable = AtomicMarkableReference("x", false)
                val stamped = AtomicStampedReference("x", 0)
                val longAdder = LongAdder()
                val longAccumulator = LongAccumulator(Long::plus, 0L)
                val doubleAdder = DoubleAdder()
                val doubleAccumulator = DoubleAccumulator(Double::plus, 0.0)
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "java.util.concurrent.atomic.AtomicMarkableReference")
        assertContains(result.renderedMessages(), "java.util.concurrent.atomic.AtomicStampedReference")
        assertContains(result.renderedMessages(), "java.util.concurrent.atomic.LongAdder")
        assertContains(result.renderedMessages(), "java.util.concurrent.atomic.LongAccumulator")
        assertContains(result.renderedMessages(), "java.util.concurrent.atomic.DoubleAdder")
        assertContains(result.renderedMessages(), "java.util.concurrent.atomic.DoubleAccumulator")
    }

    @Test
    fun `top level lock and synchronizer vals are rejected by default blacklist`() {
        val result =
            compileSnippet(
                """
                package sample

                import java.util.concurrent.CountDownLatch
                import java.util.concurrent.CyclicBarrier
                import java.util.concurrent.Phaser
                import java.util.concurrent.Semaphore
                import java.util.concurrent.locks.ReentrantLock
                import java.util.concurrent.locks.ReentrantReadWriteLock

                val lock = ReentrantLock()
                val readWriteLock = ReentrantReadWriteLock()
                val semaphore = Semaphore(1)
                val latch = CountDownLatch(1)
                val barrier = CyclicBarrier(2)
                val phaser = Phaser(1)
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "java.util.concurrent.locks.ReentrantLock")
        assertContains(result.renderedMessages(), "java.util.concurrent.locks.ReentrantReadWriteLock")
        assertContains(result.renderedMessages(), "java.util.concurrent.Semaphore")
        assertContains(result.renderedMessages(), "java.util.concurrent.CountDownLatch")
        assertContains(result.renderedMessages(), "java.util.concurrent.CyclicBarrier")
        assertContains(result.renderedMessages(), "java.util.concurrent.Phaser")
    }

    @Test
    fun `top level kotlin concurrent atomics are rejected by default blacklist`() {
        val result =
            compileSnippet(
                """
                @file:OptIn(kotlin.ExperimentalAtomicApi::class)

                package sample

                import kotlin.concurrent.atomics.AtomicArray
                import kotlin.concurrent.atomics.AtomicBoolean
                import kotlin.concurrent.atomics.AtomicInt
                import kotlin.concurrent.atomics.AtomicIntArray
                import kotlin.concurrent.atomics.AtomicLong
                import kotlin.concurrent.atomics.AtomicLongArray
                import kotlin.concurrent.atomics.AtomicReference

                val intCounter = AtomicInt(0)
                val longCounter = AtomicLong(0)
                val enabled = AtomicBoolean(false)
                val ref = AtomicReference("x")
                val intArray = AtomicIntArray(intArrayOf(1))
                val longArray = AtomicLongArray(longArrayOf(1L))
                val refArray = AtomicArray(arrayOf("x"))
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "kotlin.concurrent.atomics.AtomicInt")
        assertContains(result.renderedMessages(), "kotlin.concurrent.atomics.AtomicLong")
        assertContains(result.renderedMessages(), "kotlin.concurrent.atomics.AtomicBoolean")
        assertContains(result.renderedMessages(), "kotlin.concurrent.atomics.AtomicReference")
        assertContains(result.renderedMessages(), "kotlin.concurrent.atomics.AtomicIntArray")
        assertContains(result.renderedMessages(), "kotlin.concurrent.atomics.AtomicLongArray")
        assertContains(result.renderedMessages(), "kotlin.concurrent.atomics.AtomicArray")
    }

    @Test
    fun `top level coroutines sync and channel vals are rejected by default blacklist`() {
        val result =
            compileSnippet(
                """
                package sample

                import kotlinx.coroutines.channels.Channel
                import kotlinx.coroutines.sync.Mutex
                import kotlinx.coroutines.sync.Semaphore

                val mutex = Mutex()
                val semaphore = Semaphore(1)
                val channel = Channel<Int>()
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "kotlinx.coroutines.sync.Mutex")
        assertContains(result.renderedMessages(), "kotlinx.coroutines.sync.Semaphore")
        assertContains(result.renderedMessages(), "kotlinx.coroutines.channels.Channel")
    }

    @Test
    fun `top level mutable flow vals are rejected by default blacklist`() {
        val result =
            compileSnippet(
                """
                package sample

                import kotlinx.coroutines.flow.MutableSharedFlow
                import kotlinx.coroutines.flow.MutableStateFlow

                val state = MutableStateFlow(0)
                val events = MutableSharedFlow<Int>()
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "kotlinx.coroutines.flow.MutableStateFlow")
        assertContains(result.renderedMessages(), "kotlinx.coroutines.flow.MutableSharedFlow")
    }

    @Test
    fun `top level atomicfu vals are rejected by default blacklist`() {
        val result =
            compileSnippet(
                """
                package sample

                import kotlinx.atomicfu.AtomicBoolean
                import kotlinx.atomicfu.AtomicInt
                import kotlinx.atomicfu.AtomicLong
                import kotlinx.atomicfu.AtomicRef
                import kotlinx.atomicfu.atomic

                val intCounter: AtomicInt = atomic(0)
                val longCounter: AtomicLong = atomic(0L)
                val enabled: AtomicBoolean = atomic(false)
                val ref: AtomicRef<String> = atomic("x")
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "kotlinx.atomicfu.AtomicInt")
        assertContains(result.renderedMessages(), "kotlinx.atomicfu.AtomicLong")
        assertContains(result.renderedMessages(), "kotlinx.atomicfu.AtomicBoolean")
        assertContains(result.renderedMessages(), "kotlinx.atomicfu.AtomicRef")
    }

    @Test
    fun `top level string builders are rejected by default blacklist`() {
        val result =
            compileSnippet(
                """
                package sample

                val builder: kotlin.text.StringBuilder = StringBuilder()
                val buffer = java.lang.StringBuffer()
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "java.lang.StringBuilder")
        assertContains(result.renderedMessages(), "java.lang.StringBuffer")
    }

    @Test
    fun `custom blacklisted types extend defaults`() {
        val result =
            compileSnippet(
                """
                package sample

                class MutableBox(var value: Int)

                val box = MutableBox(0)
                val users = mutableListOf<String>()
                """.trimIndent(),
                blacklistedTypes = listOf("sample.MutableBox"),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample.MutableBox")
        assertContains(result.renderedMessages(), "kotlin.collections.MutableList")
    }

    @Test
    fun `custom nested blacklisted types are rejected`() {
        val result =
            compileSnippet(
                """
                package sample

                class Outer {
                    class MutableBox(var value: Int)
                }

                val box = Outer.MutableBox(0)
                """.trimIndent(),
                blacklistedTypes = listOf("sample.Outer.MutableBox"),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "sample.Outer.MutableBox")
    }

    @Test
    fun `subtypes of blacklisted mutable carriers are rejected`() {
        val result =
            compileSnippet(
                """
                package sample

                class MutableStringList : MutableList<String> by mutableListOf()

                val users = MutableStringList()
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "kotlin.collections.MutableList")
    }

    @Test
    fun `object singleton implementing blacklisted mutable carrier is rejected`() {
        val result =
            compileSnippet(
                """
                package sample

                object Users : MutableList<String> by mutableListOf()
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "object singleton")
        assertContains(result.renderedMessages(), "kotlin.collections.MutableList")
    }

    @Test
    fun `annotated object singleton implementing blacklisted mutable carrier requires opt in at use site`() {
        val result =
            compileSources(
                mapOf(
                    "sample/Users.kt" to
                        """
                        package sample

                        import one.wabbit.noglobals.RequiresGlobalState

                        @RequiresGlobalState
                        object Users : MutableList<String> by mutableListOf()
                        """.trimIndent(),
                    "sample/Use.kt" to
                        """
                        package sample

                        fun countUsers(): Int = Users.size
                        """.trimIndent(),
                ),
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "global mutable state")
    }

    @Test
    fun `ordinary immutable top level vals still compile`() {
        val result =
            compileSnippet(
                """
                package sample

                val answer: Int = 42
                val label: String = "ok"
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `mutable properties inside anonymous object expressions are ignored`() {
        val result =
            compileSnippet(
                """
                package sample

                fun makeThing(): Any =
                    object {
                        var counter: Int = 0
                    }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `top level val holding anonymous object with mutable property is rejected`() {
        val result =
            compileSnippet(
                """
                package sample

                val holder =
                    object {
                        var counter: Int = 0
                    }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "top-level val")
        assertContains(result.renderedMessages(), "anonymous object")
    }

    @Test
    fun `local vals holding anonymous objects with mutable members are ignored`() {
        val result =
            compileSnippet(
                """
                package sample

                fun render(): Int {
                    val holder =
                        object {
                            var counter: Int = 0
                        }
                    holder.counter += 1
                    return holder.counter
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `local vars are ignored`() {
        val result =
            compileSnippet(
                """
                package sample

                fun render(): Int {
                    var counter = 0
                    counter += 1
                    return counter
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `data object mutable list vals are rejected by default blacklist`() {
        val result =
            compileSnippet(
                """
                package sample

                data object Globals {
                    val users = mutableListOf<String>()
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "object singleton")
        assertContains(result.renderedMessages(), "kotlin.collections.MutableList")
    }

    @Test
    fun `immutable globals are ignored`() {
        val result =
            compileSnippet(
                """
                package sample

                val answer: Int = 42

                object Globals {
                    val label: String = "ok"
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `enum class body vars are rejected`() {
        val result =
            compileSnippet(
                """
                package sample

                enum class E {
                    A;

                    var counter: Int = 0
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "enum class")
    }

    @Test
    fun `enum entry body vars are rejected`() {
        val result =
            compileSnippet(
                """
                package sample

                enum class E {
                    A {
                        var counter: Int = 0
                    }
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "enum entry")
    }

    @Test
    fun `enum entry val holding anonymous object with mutable property is rejected`() {
        val result =
            compileSnippet(
                """
                package sample

                enum class E {
                    A {
                        val holder =
                            object {
                                var counter: Int = 0
                            }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        val diagnostics = result.globalMutableStateDiagnostics()
        assertEquals(1, diagnostics.size, result.renderedMessages())
        assertContains(diagnostics.single(), "enum entry")
        assertContains(diagnostics.single(), "anonymous object")
    }

    @Test
    fun `local class inside enum entry method is ignored`() {
        val result =
            compileSnippet(
                """
                package sample

                enum class E {
                    A {
                        fun makeBox(): Any {
                            class Box {
                                var counter: Int = 0
                            }
                            return Box()
                        }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `anonymous object holding only local class inside method does not false positive`() {
        val result =
            compileSnippet(
                """
                package sample

                val holder =
                    object {
                        fun makeBox(): Any {
                            class Box {
                                var counter: Int = 0
                            }
                            return Box()
                        }
                    }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, result.renderedMessages())
    }

    @Test
    fun `lateinit top level vars are called out explicitly`() {
        val result =
            compileSnippet(
                """
                package sample

                lateinit var service: String
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "lateinit top-level var")
    }

    @Test
    fun `volatile top level vars are rejected`() {
        val result =
            compileSnippet(
                """
                package sample

                @Volatile
                var counter: Int = 0
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "top-level var")
    }

    @Test
    fun `annotated singleton property still requires use site opt in`() {
        val result =
            compileSources(
                mapOf(
                    "sample/Globals.kt" to
                        """
                        package sample

                        import one.wabbit.noglobals.RequiresGlobalState

                        object Globals {
                            @RequiresGlobalState
                            var counter: Int = 0
                        }
                        """.trimIndent(),
                    "sample/Use.kt" to
                        """
                        package sample

                        fun readCounter(): Int = Globals.counter
                        """.trimIndent(),
                ),
                includeAnnotationDefinitions = true,
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode, result.renderedMessages())
        assertContains(result.renderedMessages(), "global mutable state")
    }
}

private data class CompileResult(
    val exitCode: ExitCode,
    val messages: List<String>,
) {
    fun renderedMessages(): String = messages.joinToString(separator = "\n")

    fun globalMutableStateDiagnostics(): List<String> =
        messages.filter { message -> "Global mutable state detected" in message }
}

private fun compileSnippet(
    source: String,
    includeAnnotationDefinitions: Boolean = false,
    enabled: Boolean = true,
    blacklistedTypes: List<String> = emptyList(),
): CompileResult =
    compileSources(
        sources = mapOf("sample/Test.kt" to source),
        includeAnnotationDefinitions = includeAnnotationDefinitions,
        enabled = enabled,
        blacklistedTypes = blacklistedTypes,
    )

private fun compileSources(
    sources: Map<String, String>,
    includeAnnotationDefinitions: Boolean = false,
    enabled: Boolean = true,
    blacklistedTypes: List<String> = emptyList(),
): CompileResult {
    val tempDir = Files.createTempDirectory("no-globals-plugin-test")
    try {
        val sourceRoot = tempDir.resolve("src").createDirectories()
        val outputRoot = tempDir.resolve("out").createDirectories()
        val materializedSources =
            buildMap {
                putAll(sources)
                if (includeAnnotationDefinitions) {
                    putAll(libraryAnnotationDefinitions())
                }
            }
        val sourceFiles =
            materializedSources.map { (relativePath, content) ->
                val sourceFile = sourceRoot.resolve(relativePath)
                sourceFile.parent.createDirectories()
                sourceFile.writeText(content)
                sourceFile
            }

        val arguments =
            K2JVMCompilerArguments().apply {
                freeArgs = sourceFiles.map(Path::toString)
                destination = outputRoot.toString()
                classpath = System.getProperty("java.class.path")
                noStdlib = true
                noReflect = true
                pluginClasspaths =
                    arrayOf(
                        pluginArtifact().toString(),
                    )
                pluginOptions =
                    buildList {
                        add("plugin:$TEST_PLUGIN_ID:enabled=$enabled")
                        blacklistedTypes.forEach { blacklistedType ->
                            add("plugin:$TEST_PLUGIN_ID:blacklistedType=$blacklistedType")
                        }
                    }.toTypedArray()
                jvmTarget = "21"
            }

        val collector = CollectingMessageCollector()
        val exitCode = K2JVMCompiler().exec(collector, Services.EMPTY, arguments)
        return CompileResult(exitCode, collector.messages)
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}

private fun libraryAnnotationDefinitions(): Map<String, String> {
    val annotationRoot = repositoryRoot().resolve("library/src/commonMain/kotlin/one/wabbit/noglobals")
    return Files.list(annotationRoot).use { paths ->
        paths
            .filter { path -> path.fileName.toString().endsWith(".kt") }
            .toList()
            .sortedBy { path -> path.fileName.toString() }
            .associate { path ->
                "one/wabbit/noglobals/${path.fileName}" to path.readText()
            }
    }
}

private fun repositoryRoot(): Path =
    generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { current -> current.parent }
        .firstOrNull { current ->
            Files.isDirectory(current.resolve("library/src/commonMain/kotlin/one/wabbit/noglobals"))
        } ?: error("Could not locate kotlin-no-globals repository root from ${System.getProperty("user.dir")}")

private fun pluginArtifact(): Path {
    return locatePluginArtifact(
        explicitPluginJarPath = System.getProperty("kotlin.no.globals.test.pluginJar"),
        repositoryRoot = repositoryRoot(),
        kotlinVersion = System.getProperty("kotlinVersion"),
    )
}

internal fun locatePluginArtifact(
    explicitPluginJarPath: String?,
    repositoryRoot: Path,
    kotlinVersion: String?,
): Path {
    explicitPluginJarPath
        ?.takeIf(String::isNotBlank)
        ?.let { configuredPath ->
            val path = Path.of(configuredPath)
            check(Files.isRegularFile(path)) {
                "Configured compiler plugin test jar does not exist: $path"
            }
            return path
        }

    val libsDirectory = repositoryRoot.resolve("compiler-plugin/build/libs")
    return resolvePluginArtifact(libsDirectory, kotlinVersion)
}

internal fun resolvePluginArtifact(
    libsDirectory: Path,
    kotlinVersion: String? = null,
): Path {
    return Files.list(libsDirectory).use { paths ->
        val candidates =
            paths
            .filter { path ->
                val name = path.fileName.toString()
                name.startsWith("kotlin-no-globals-plugin-") &&
                    name.endsWith(".jar") &&
                    !name.endsWith("-sources.jar") &&
                    !name.endsWith("-javadoc.jar")
            }.toList()
            .sortedBy { path -> path.fileName.toString() }

        val versionMatchedCandidates =
            kotlinVersion
                ?.takeIf(String::isNotBlank)
                ?.let { expectedKotlinVersion ->
                    candidates.filter { path ->
                        "-kotlin-$expectedKotlinVersion" in path.fileName.toString()
                    }
                } ?: candidates

        val effectiveCandidates = if (versionMatchedCandidates.isNotEmpty()) versionMatchedCandidates else candidates

        when (effectiveCandidates.size) {
            1 -> effectiveCandidates.single()
            0 -> throw IllegalStateException("Could not locate built compiler plugin runtime jar in $libsDirectory")
            else ->
                throw IllegalStateException(
                    "Expected exactly one compiler plugin runtime jar in $libsDirectory but found: " +
                        effectiveCandidates.joinToString { path -> path.fileName.toString() },
                )
        }
    }
}

private class CollectingMessageCollector : MessageCollector {
    private val _messages = mutableListOf<String>()

    val messages: List<String>
        get() = _messages

    override fun clear() {
        _messages.clear()
    }

    override fun hasErrors(): Boolean =
        _messages.any { message -> message.startsWith("${CompilerMessageSeverity.ERROR}:") }

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?,
    ) {
        if (severity == CompilerMessageSeverity.LOGGING) {
            return
        }
        val locationPrefix =
            location?.let { current ->
                buildString {
                    append(current.path)
                    if (current.line > 0) {
                        append(':')
                        append(current.line)
                    }
                    if (current.column > 0) {
                        append(':')
                        append(current.column)
                    }
                    append(": ")
                }
            }.orEmpty()
        _messages += "$severity: $locationPrefix$message"
    }
}
