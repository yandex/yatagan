package com.yandex.daggerlite.jap

import com.tschuchort.compiletesting.KotlinCompilation
import com.yandex.daggerlite.testing.CompileTestDriver
import com.yandex.daggerlite.testing.CompileTestDriverBase
import java.io.File
import java.net.URLClassLoader
import kotlin.test.assertContains
import kotlin.test.expect

class JapCompileTestDriver : CompileTestDriverBase() {
    override fun failsToCompile(block: CompileTestDriver.CompilationResultClause.() -> Unit) {
        val compilation = setupCompilation()
        expect(KotlinCompilation.ExitCode.COMPILATION_ERROR) {
            val result = compilation.compile()
            JapCompilationResultClause(
                generation = compilation,
                result = result,
                compiledClassesLoader = null,
            ).block()

            compilation.japGeneratedSources().forEach { file ->
                println("Generated file: //$file")
            }

            result.exitCode
        }
    }

    override fun compilesSuccessfully(block: CompileTestDriver.CompilationResultClause.() -> Unit) {
        val compilation = setupCompilation()
        try {
            expect(KotlinCompilation.ExitCode.OK) {
                val result = compilation.compile()
                JapCompilationResultClause(
                    generation = compilation,
                    result = result,
                    compiledClassesLoader = URLClassLoader(
                        arrayOf(compilation.classesDir.toURI().toURL()),
                        JapCompileTestDriver::class.java.classLoader
                    ),
                ).apply {
                    withNoErrors()
                    block()
                }
                result.exitCode
            }
        } finally {
            for (source in compilation.japGeneratedSources()) {
                println("Generated file://$source")
            }
        }
    }

    override val backendUnderTest: CompileTestDriver.Backend
        get() = CompileTestDriver.Backend.Jap

    private fun setupCompilation() = KotlinCompilation().apply {
        precompileIfNeeded()?.let { precompiled ->
            classpaths = classpaths + precompiled
        }
        basicKotlinCompilationSetup()
        sources = sourceFiles
        annotationProcessors = listOf(JapDaggerLiteProcessor())
    }

    private class JapCompilationResultClause(
        private val generation: KotlinCompilation?,
        result: KotlinCompilation.Result,
        compiledClassesLoader: ClassLoader?,
    ) : CompilationResultClauseBase(result, compiledClassesLoader) {

        override fun generatesJavaSources(name: String) {
            if (generation != null) {
                assertContains(
                    generation.kaptSourceDir
                        .resolve(name.substringBeforeLast('.').replace('.', '/'))
                        .listFiles()
                        ?.map { it.name } ?: emptyList(),
                    "${name.substringAfterLast('.')}.java",
                )
            }
        }
    }

    private fun KotlinCompilation.japGeneratedSources(): Sequence<File> {
        return kaptSourceDir.walk().filter { it.isFile && it.extension == "java" }
    }
}
