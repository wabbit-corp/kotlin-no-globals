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
    fun `setter annotated top level var compiles`() {
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
}

private data class CompileResult(
    val exitCode: ExitCode,
    val messages: List<String>,
) {
    fun renderedMessages(): String = messages.joinToString(separator = "\n")
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
                        add("plugin:$NO_GLOBALS_PLUGIN_ID:enabled=$enabled")
                        blacklistedTypes.forEach { blacklistedType ->
                            add("plugin:$NO_GLOBALS_PLUGIN_ID:blacklistedType=$blacklistedType")
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
    val repositoryRoot = repositoryRoot()
    val libsDirectory = repositoryRoot.resolve("compiler-plugin/build/libs")
    return Files.list(libsDirectory).use { paths ->
        paths
            .filter { path ->
                val name = path.fileName.toString()
                name.endsWith(".jar")
            }.findFirst()
            .orElseThrow { IllegalStateException("Could not locate built compiler plugin jar in $libsDirectory") }
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
