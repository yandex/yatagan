package com.yandex.daggerlite.testing.tests

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspArgs
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.yandex.daggerlite.processor.common.Options
import com.yandex.daggerlite.processor.ksp.KspDaggerLiteProcessorProvider
import java.io.File

class KspCompileTestDriver : CompileTestDriverBase() {
    override fun doCompile(): TestCompilationResult {
        val firstStageResult = super.doCompile()
        if (!firstStageResult.success) {
            return firstStageResult
        }

        // KSP doesn't automatically compile its generated sources.
        // So second compilation is required just for the generated sources.
        val secondStageCompilation = super.createKotlinCompilation().apply {
            classpaths = firstStageResult.runtimeClasspath
            sources = sourceFiles + firstStageResult.generatedFiles.map(SourceFile::fromPath)
        }
        val secondStageResult = secondStageCompilation.compile()
        return firstStageResult.copy(
            runtimeClasspath = firstStageResult.runtimeClasspath + secondStageCompilation.classesDir,
            success = secondStageResult.exitCode == ExitCode.OK,
        )
    }

    override fun createKotlinCompilation() = super.createKotlinCompilation().apply {
        sources = sourceFiles
        // Can't enable `withCompilation` here, as KSP stops failing the build on errors.
        // See the issue https://github.com/google/ksp/issues/974
        symbolProcessorProviders = listOf(KspDaggerLiteProcessorProvider())
        kspArgs[Options.MaxIssueEncounterPaths.key] = "100"
    }

    override fun getGeneratedFiles(from: KotlinCompilation): Collection<File> {
        return from.kspSourcesDir.walk()
            .filter { it.isFile && it.extension == "java" }
            .toList()
    }

    override val backendUnderTest: Backend
        get() = Backend.Ksp
}
