package com.yandex.daggerlite.testing.tests

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.yandex.daggerlite.generated.CompiledApiClasspath
import com.yandex.daggerlite.generated.DynamicApiClasspath
import com.yandex.daggerlite.generated.DynamicOptimizedApiClasspath
import com.yandex.daggerlite.processor.common.LoggerDecorator
import com.yandex.daggerlite.testing.source_set.SourceSet
import org.junit.Assert
import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

abstract class CompileTestDriverBase protected constructor(
    private val apiType: ApiType = ApiType.Compiled,
) : CompileTestDriver {
    private val mainSourceSet = SourceSet()
    private var precompiledModuleOutputDir: File? = null

    override val testNameRule = TestNameRule()

    final override val sourceFiles: List<SourceFile>
        get() = mainSourceSet.sourceFiles

    final override fun givenJavaSource(name: String, source: String) {
        mainSourceSet.givenJavaSource(name, source)
    }

    final override fun givenKotlinSource(name: String, source: String) {
        mainSourceSet.givenKotlinSource(name, source)
    }

    final override fun includeFromSourceSet(sourceSet: SourceSet) {
        mainSourceSet.includeFromSourceSet(sourceSet)
    }

    override fun includeAllFromDirectory(sourceDir: File) {
        mainSourceSet.includeAllFromDirectory(sourceDir)
    }

    final override fun givenPrecompiledModule(sources: SourceSet) {
        check(precompiledModuleOutputDir == null) {
            "Can't have multiple precompiled modules"
        }

        val compilation = createBaseKotlinCompilation().apply {
            this.sources = sources.sourceFiles
        }
        val result = compilation.compile()
        if (result.exitCode != KotlinCompilation.ExitCode.OK) {
            throw RuntimeException("Pre-compilation failed, check the code")
        }

        precompiledModuleOutputDir = result.outputDirectory
    }

    data class TestCompilationResult(
        val runtimeClasspath: List<File>,
        val messageLog: String,
        val success: Boolean,
        val generatedFiles: Collection<File>,
    )

    protected open fun doCompile(): TestCompilationResult {
        val compilation = createKotlinCompilation()
        val result = compilation.compile()
        return TestCompilationResult(
            runtimeClasspath = compilation.classpaths + compilation.classesDir,
            messageLog = result.messages,
            success = result.exitCode == KotlinCompilation.ExitCode.OK,
            generatedFiles = getGeneratedFiles(compilation),
        )
    }

    protected open fun getGeneratedFiles(from: KotlinCompilation): Collection<File> {
        return emptyList()
    }

    protected open fun runRuntimeTest(test: Method) {
        test.invoke(null)
    }

    override fun compileRunAndValidate() {
        val goldenResourcePath = "golden/${testNameRule.testClassSimpleName}/${testNameRule.testMethodName}.golden.txt"

        val (runtimeClasspath, messageLog, success, generatedFiles) = doCompile()
        val strippedLog = normalizeMessages(messageLog)

        if (goldenSourceDirForUpdate != null) {
            val goldenSourcePath = Path(goldenSourceDirForUpdate).resolve(goldenResourcePath)
            if (strippedLog.isBlank()) {
                goldenSourcePath.deleteIfExists()
            } else {
                goldenSourcePath.parent.createDirectories()
                goldenSourcePath.writeText(strippedLog)
            }
            println("Updated $goldenSourcePath")
            return
        }

        try {
            val goldenOutput = javaClass.getResourceAsStream("/$goldenResourcePath")?.bufferedReader()?.readText() ?: ""
            Assert.assertEquals(goldenOutput, strippedLog)

            if (success) {
                // find runtime test
                val classLoader = makeClassLoader(runtimeClasspath)
                try {
                    runRuntimeTest(classLoader.loadClass("test.TestCaseKt").getDeclaredMethod("test"))
                } catch (e: ClassNotFoundException) {
                    println("NOTE: No runtime test detected.")
                } catch (e: NoSuchMethodException) {
                    println("NOTE: No runtime test detected in TestCaseKt class.")
                }
            } else {
                Assert.assertTrue("Compilation failed, yet expected output is blank", goldenOutput.isNotBlank())
            }
        } finally {
            // print generated files
            for (generatedFile in generatedFiles) {
                println("Generated file://${generatedFile.absolutePath}")
            }
        }
    }

    private fun createBaseKotlinCompilation(): KotlinCompilation {
        return KotlinCompilation().apply {
            verbose = false
            inheritClassPath = false
            javacArguments += "-Xdiags:verbose"
            kotlincArguments = listOf(
                "-opt-in=com.yandex.daggerlite.ConditionsApi",
                "-opt-in=com.yandex.daggerlite.VariantApi",
            )
            classpaths = buildList {
                when (apiType) {
                    ApiType.Compiled -> CompiledApiClasspath
                    ApiType.Dynamic -> DynamicApiClasspath
                    ApiType.DynamicOptimized -> DynamicOptimizedApiClasspath
                }.split(':').forEach { add(File(it)) }
                precompiledModuleOutputDir?.let { add(it) }
            }
        }
    }

    protected open fun createKotlinCompilation(): KotlinCompilation {
        return createBaseKotlinCompilation()
    }

    protected fun makeClassLoader(classpaths: List<File>): ClassLoader {
        return URLClassLoader(
            classpaths.map { it.toURI().toURL() }.toTypedArray(),
            null,
        )
    }

    enum class ApiType {
        Compiled,
        Dynamic,
        DynamicOptimized,
    }

    /**
     * Required to trim and sort error-messages, as they are not required to be issued in any particular order.
     */
    private fun normalizeMessages(log: String): String {
        return buildList {
            for (messageMatch in LoggerDecorator.MessageRegex.findAll(log)) {
                val (kind, message) = messageMatch.destructured
                add("$kind: ${message.trimIndent().stripColor().trim()}")
            }
            sort()
        }.joinToString(separator = "\n$MessageSeparator\n\n").trim()
    }

    companion object {
        private fun String.stripColor(): String {
            return replace(AnsiColorSequenceRegex, "")
        }

        private val AnsiColorSequenceRegex = "\u001b\\[.*?m".toRegex()

        private val MessageSeparator = "~".repeat(80)

        val goldenSourceDirForUpdate: String? = System.getProperty("com.yandex.daggerlite.updateGoldenFiles")

        val isInUpdateGoldenMode: Boolean
            get() = goldenSourceDirForUpdate != null
    }
}
