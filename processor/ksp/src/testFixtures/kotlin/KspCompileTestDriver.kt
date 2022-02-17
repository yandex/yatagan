package com.yandex.daggerlite.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.yandex.daggerlite.ksp.KspDaggerLiteProcessorProvider
import com.yandex.daggerlite.testing.CompileTestDriver
import com.yandex.daggerlite.testing.CompileTestDriverBase
import java.io.File
import java.net.URLClassLoader
import kotlin.test.assertContains
import kotlin.test.expect

class KspCompileTestDriver : CompileTestDriverBase() {
    override fun failsToCompile(block: CompileTestDriver.CompilationResultClause.() -> Unit) {
        expect(KotlinCompilation.ExitCode.COMPILATION_ERROR) {
            val compilation = setupFirstRoundCompilation(
                precompiled = precompileIfNeeded(),
            )
            val result = compilation.compile()
            KspCompilationResultClause(
                generation = compilation,
                result = result,
                compiledClassesLoader = null,
            ).block()
            result.exitCode
        }
    }

    override fun compilesSuccessfully(block: CompileTestDriver.CompilationResultClause.() -> Unit) {
        val precompiled = precompileIfNeeded()
        val firstRound = setupFirstRoundCompilation(precompiled)
        try {
            expect(KotlinCompilation.ExitCode.OK) {
                val result = firstRound.compile()
                KspCompilationResultClause(
                    generation = firstRound,
                    result = result,
                    compiledClassesLoader = null,
                ).apply {
                    withNoErrors()
                    block()
                }
                result.exitCode
            }
            expect(KotlinCompilation.ExitCode.OK) {
                // Second round is required to validate that generated code compiles successfully
                val secondRound = KotlinCompilation().apply {
                    basicKotlinCompilationSetup()
                    if (precompiled != null) {
                        classpaths = classpaths + precompiled
                    }
                    sources = firstRound.sources + firstRound.kspGeneratedSources().map(SourceFile::fromPath)
                }
                val result = secondRound.compile()
                KspCompilationResultClause(
                    generation = null,
                    result = result,
                    compiledClassesLoader = URLClassLoader(
                        arrayOf(secondRound.classesDir.toURI().toURL()),
                        KspCompileTestDriver::class.java.classLoader
                    ),
                    checkMessageText = false,
                ).apply {
                    block()
                    withNoErrors()
                }
                result.exitCode
            }
        } finally {
            firstRound.kspGeneratedSources().forEach { file ->
                println("Content of the file://$file")
            }
        }
    }

    override val backendUnderTest: CompileTestDriver.Backend
        get() = CompileTestDriver.Backend.Ksp

    private fun setupFirstRoundCompilation(
        precompiled: File?,
    ) = KotlinCompilation().apply {
        if (precompiled != null) {
            classpaths = classpaths + precompiled
        }
        basicKotlinCompilationSetup()
        sources = sourceFiles
        symbolProcessorProviders = listOf(KspDaggerLiteProcessorProvider())
    }

    private inner class KspCompilationResultClause(
        private val generation: KotlinCompilation?,
        result: KotlinCompilation.Result,
        compiledClassesLoader: ClassLoader?,
        override val checkMessageText: Boolean = true,
    ) : CompilationResultClauseBase(result, compiledClassesLoader) {
        override fun generatesJavaSources(name: String) {
            if (generation != null) {
                assertContains(
                    generation.kspSourcesDir
                        .resolve("java")
                        .resolve(name.substringBeforeLast('.').replace('.', '/'))
                        .listFiles()
                        ?.map { it.name } ?: emptyList(),
                    "${name.substringAfterLast('.')}.java",
                )
            }
        }
    }
}

private fun KotlinCompilation.kspGeneratedSources(): Sequence<File> {
    return kspSourcesDir.walk()
        .filter { it.isFile && it.extension == "java" }
}
