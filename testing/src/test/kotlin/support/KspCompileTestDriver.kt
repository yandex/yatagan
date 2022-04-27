package com.yandex.daggerlite.testing.support

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.ksp.KspDaggerLiteProcessorProvider
import java.io.File

class KspCompileTestDriver : CompileTestDriverBase() {

    override fun doValidate(): ValidationResult {
        val firstStageCompilation = createKotlinCompilation().apply {
            sources = sourceFiles
            symbolProcessorProviders = listOf(KspDaggerLiteProcessorProvider())
        }
        val firstStageResult = firstStageCompilation.compile()

        val kspGeneratedSources = firstStageCompilation.kspGeneratedSources().memoize()
        val secondStageCompilation = createKotlinCompilation().apply {
            sources = sourceFiles + kspGeneratedSources.map(SourceFile::fromPath)
        }
        val secondStageResult = secondStageCompilation.compile()
        return ValidationResult(
            runtimeClasspath = buildList {
                addAll(firstStageCompilation.classpaths)
                add(firstStageCompilation.classesDir)
                add(secondStageCompilation.classesDir)
            },
            messageLog = firstStageResult.messages,
            success = (firstStageResult.exitCode and secondStageResult.exitCode) == ExitCode.OK,
            generatedFiles = kspGeneratedSources.toList()
        )
    }

    private infix fun ExitCode.and(rhs: ExitCode): ExitCode {
        return when (this) {
            ExitCode.OK -> rhs
            else -> this
        }
    }

    private fun KotlinCompilation.kspGeneratedSources(): Sequence<File> {
        return kspSourcesDir.walk()
            .filter { it.isFile && it.extension == "java" }
    }

    override val backendUnderTest: Backend
        get() = Backend.Ksp
}
