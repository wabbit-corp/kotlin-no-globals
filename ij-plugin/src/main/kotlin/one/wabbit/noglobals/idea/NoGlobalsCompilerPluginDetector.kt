// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.noglobals.idea

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.facet.KotlinFacet

internal const val EXTERNAL_K2_COMPILER_PLUGINS_REGISTRY_KEY =
    "kotlin.k2.only.bundled.compiler.plugins.enabled"
internal const val NO_GLOBALS_COMPILER_PLUGIN_MARKER = "kotlin-no-globals-plugin"
internal const val NO_GLOBALS_GRADLE_PLUGIN_ID = "one.wabbit.no-globals"
private const val NO_GLOBALS_GRADLE_PLUGIN_ARTIFACT_MARKER = "kotlin-no-globals-gradle-plugin"
private const val MAX_GRADLE_BUILD_SCAN_DEPTH = 6
private val GRADLE_PLUGIN_REFERENCE_PATTERNS =
    listOf(
        Regex("""(?m)^\s*id\s*\(\s*["']one\.wabbit\.no-globals["']\s*\)"""),
        Regex("""(?m)^\s*id\s+["']one\.wabbit\.no-globals["']"""),
        Regex("""(?m)^\s*id\s*=\s*["']one\.wabbit\.no-globals["']"""),
        Regex("""(?m)^\s*[\w.-]+\s*=\s*\{[^}\n]*\bid\s*=\s*["']one\.wabbit\.no-globals["'][^}\n]*\}"""),
        Regex("""(?m)^\s*apply\s+plugin\s*:\s*["']one\.wabbit\.no-globals["']"""),
        Regex("""(?m)^\s*(?:classpath|implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|ksp|kapt)\s*\(\s*["'][^"'\n]*one\.wabbit:kotlin-no-globals-gradle-plugin(?::[^"'\n]*)?["']\s*\)"""),
        Regex("""(?m)^\s*(?:classpath|implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|ksp|kapt)\s+["'][^"'\n]*one\.wabbit:kotlin-no-globals-gradle-plugin(?::[^"'\n]*)?["']"""),
        Regex("""(?m)^\s*(?:classpath|implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|ksp|kapt)\s+group\s*:\s*["']one\.wabbit["']\s*,\s*name\s*:\s*["']kotlin-no-globals-gradle-plugin["'](?:\s*,\s*version\s*:\s*["'][^"'\n]+["'])?"""),
        Regex("""(?m)^\s*module\s*=\s*["'][^"'\n]*one\.wabbit:kotlin-no-globals-gradle-plugin(?::[^"'\n]*)?["']"""),
        Regex("""(?m)^\s*[\w.-]+\s*=\s*\{[^}\n]*\bmodule\s*=\s*["'][^"'\n]*one\.wabbit:kotlin-no-globals-gradle-plugin(?::[^"'\n]*)?["'][^}\n]*\}"""),
    )

internal data class NoGlobalsCompilerPluginMatch(
    val ownerName: String,
    val classpaths: List<String>,
)

internal data class NoGlobalsCompilerPluginScan(
    val projectLevelMatch: NoGlobalsCompilerPluginMatch?,
    val moduleMatches: List<NoGlobalsCompilerPluginMatch>,
    val gradleBuildFiles: List<String>,
) {
    val hasImportedCompilerPluginMatches: Boolean
        get() = projectLevelMatch != null || moduleMatches.isNotEmpty()

    val hasMatches: Boolean
        get() = hasImportedCompilerPluginMatches || gradleBuildFiles.isNotEmpty()

    val requiresGradleImport: Boolean
        get() = gradleBuildFiles.isNotEmpty() && !hasImportedCompilerPluginMatches
}

internal object NoGlobalsCompilerPluginDetector {
    fun scan(project: Project): NoGlobalsCompilerPluginScan {
        val gradleBuildFiles =
            project.basePath?.let { basePath ->
                matchingGradleBuildFiles(Path.of(basePath))
            }.orEmpty()
        val projectClasspaths =
            matchingClasspaths(
                KotlinCommonCompilerArgumentsHolder.getInstance(project)
                    .settings
                    .pluginClasspaths
                    .orEmpty()
                    .asList(),
            )
        val projectMatch =
            projectClasspaths.takeIf { it.isNotEmpty() }?.let { classpaths ->
                NoGlobalsCompilerPluginMatch(
                    ownerName = project.name,
                    classpaths = classpaths,
                )
            }
        val moduleMatches =
            ModuleManager.getInstance(project).modules.mapNotNull { module ->
                scanModule(module)
            }
        return NoGlobalsCompilerPluginScan(
            projectLevelMatch = projectMatch,
            moduleMatches = moduleMatches,
            gradleBuildFiles = gradleBuildFiles,
        )
    }

    fun matchingClasspaths(classpaths: Iterable<String>): List<String> =
        classpaths
            .filter(::isNoGlobalsCompilerPluginPath)
            .distinct()

    fun isNoGlobalsCompilerPluginPath(classpath: String): Boolean {
        val normalized = classpath.replace('\\', '/').lowercase()
        return normalized.contains(NO_GLOBALS_COMPILER_PLUGIN_MARKER)
    }

    fun matchingGradleBuildFiles(projectRoot: Path): List<String> {
        if (!Files.isDirectory(projectRoot)) {
            return emptyList()
        }
        Files.walk(projectRoot, MAX_GRADLE_BUILD_SCAN_DEPTH).use { paths ->
            return paths
                .filter(Files::isRegularFile)
                .map { path -> projectRoot.relativize(path).normalize() }
                .filter(::isGradleBuildFileCandidate)
                .filter { relativePath ->
                    runCatching {
                        isNoGlobalsGradlePluginReference(
                            Files.readString(projectRoot.resolve(relativePath)),
                        )
                    }.getOrDefault(false)
                }.map { relativePath ->
                    relativePath.toString().replace('\\', '/')
                }.distinct()
                .sorted()
                .collect(Collectors.toList())
        }
    }

    fun isNoGlobalsGradlePluginReference(content: String): Boolean {
        val normalized = stripCommentsPreservingStrings(content).lowercase()
        return GRADLE_PLUGIN_REFERENCE_PATTERNS.any { pattern ->
            pattern.containsMatchIn(normalized)
        }
    }

    private fun scanModule(module: Module): NoGlobalsCompilerPluginMatch? {
        val facet = KotlinFacet.get(module) ?: return null
        val classpaths =
            matchingClasspaths(
                facet.configuration.settings
                    .mergedCompilerArguments
                    ?.pluginClasspaths
                    .orEmpty()
                    .asList(),
            )
        if (classpaths.isEmpty()) {
            return null
        }
        return NoGlobalsCompilerPluginMatch(
            ownerName = module.name,
            classpaths = classpaths,
        )
    }

    private fun isGradleBuildFileCandidate(path: Path): Boolean {
        if (path.any { segment ->
                segment.toString() in setOf(".git", ".gradle", ".idea", "build", "out")
            }
        ) {
            return false
        }
        val fileName = path.fileName?.toString() ?: return false
        return fileName.endsWith(".gradle") ||
            fileName.endsWith(".gradle.kts") ||
            fileName.endsWith(".versions.toml")
    }

    private fun stripCommentsPreservingStrings(content: String): String {
        val result = StringBuilder(content.length)
        var index = 0
        while (index < content.length) {
            when {
                content.startsWith("//", index) -> {
                    index += 2
                    while (index < content.length && content[index] != '\n') {
                        index += 1
                    }
                }
                content[index] == '#' -> {
                    index += 1
                    while (index < content.length && content[index] != '\n') {
                        index += 1
                    }
                }
                content.startsWith("/*", index) -> {
                    index += 2
                    while (index < content.length && !content.startsWith("*/", index)) {
                        index += 1
                    }
                    if (index < content.length) {
                        index += 2
                    }
                }
                content.startsWith("\"\"\"", index) -> {
                    result.append("\"\"\"")
                    index += 3
                    while (index < content.length && !content.startsWith("\"\"\"", index)) {
                        result.append(content[index])
                        index += 1
                    }
                    if (index < content.length) {
                        result.append("\"\"\"")
                        index += 3
                    }
                }
                content.startsWith("'''", index) -> {
                    result.append("'''")
                    index += 3
                    while (index < content.length && !content.startsWith("'''", index)) {
                        result.append(content[index])
                        index += 1
                    }
                    if (index < content.length) {
                        result.append("'''")
                        index += 3
                    }
                }
                content[index] == '"' || content[index] == '\'' -> {
                    val quote = content[index]
                    result.append(quote)
                    index += 1
                    while (index < content.length) {
                        val current = content[index]
                        result.append(current)
                        index += 1
                        if (current == '\\' && index < content.length) {
                            result.append(content[index])
                            index += 1
                            continue
                        }
                        if (current == quote) {
                            break
                        }
                    }
                }
                else -> {
                    result.append(content[index])
                    index += 1
                }
            }
        }
        return result.toString()
    }
}
