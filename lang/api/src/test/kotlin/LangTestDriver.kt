package com.yandex.yatagan.lang

import androidx.room.compiler.processing.util.compiler.TestCompilationArguments
import androidx.room.compiler.processing.util.compiler.compile
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.yandex.yatagan.base.ObjectCacheRegistry
import com.yandex.yatagan.base.mapToArray
import com.yandex.yatagan.lang.jap.JavaxModelFactoryImpl
import com.yandex.yatagan.lang.ksp.KspModelFactoryImpl
import com.yandex.yatagan.lang.rt.RtModelFactoryImpl
import com.yandex.yatagan.testing.source_set.SourceSet
import java.io.File
import java.net.URLClassLoader
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import kotlin.io.path.createTempDirectory

typealias InspectBlock = LangModelFactory.() -> Unit

private typealias JapProcessingUtils = com.yandex.yatagan.lang.jap.ProcessingUtils
private typealias KspProcessingUtils = com.yandex.yatagan.lang.ksp.ProcessingUtils

interface LangTestDriver : SourceSet {
    /**
     * Call this in the test and make assertions inside [block].
     */
    fun inspect(block: InspectBlock)

    companion object {
        /**
         * Parameterize a test class with the returned list.
         */
        fun all(
            includeJap: Boolean = true,
            includeKsp: Boolean = true,
            includeRt: Boolean = true,
        ) = listOfNotNull(
            object : () -> LangTestDriver {
                override fun invoke() = JapLangTestDriver()
                override fun toString() = "JAP"
            }.takeIf { includeJap },
            object : () -> LangTestDriver {
                override fun invoke() = KspLangTestDriver()
                override fun toString() = "KSP"
            }.takeIf { includeKsp },
            object : () -> LangTestDriver {
                override fun invoke() = RtLangTestDriver()
                override fun toString() = "RT"
            }.takeIf { includeRt },
        )

        private abstract class LangTestDriverBase : LangTestDriver, SourceSet by SourceSet() {
            protected fun createCompilation(): TestCompilationArguments {
                return TestCompilationArguments(
                    sources = sourceFiles,
                    inheritClasspath = false,
                    classpath = StdLibClasspath.split(':').map(::File),
                    javacArguments = listOf("-Xdiags:verbose"),
                )
            }

            protected open fun setUpCompilation(
                compilation: TestCompilationArguments,
                block: InspectBlock,
            ) = compilation

            override fun inspect(block: InspectBlock) {
                ObjectCacheRegistry.use {
                    var error: Throwable? = null
                    val safeBlock: InspectBlock = {
                        try {
                            block()
                        } catch (e: Throwable) {
                            error = e
                        }
                    }
                    val compilation = createCompilation().apply {
                        setUpCompilation(this, safeBlock)
                    }
                    val tmpDir = createTempDirectory(
                        prefix = "ylt",
                    ).toFile()
                    val result = compile(
                        workingDir = tmpDir,
                        arguments = compilation,
                    )

                    error?.let { throw it }

                    check(result.success) {
                        System.err.println(result.diagnostics.values.flatten()
                            .joinToString(separator = "\n") { it.msg })
                        "Lang test should compile, yet the compilation failed"
                    }
                }
            }
        }

        private class RtLangTestDriver : LangTestDriverBase() {
            override fun inspect(block: InspectBlock) {
                val tmpDir = createTempDirectory(
                    prefix = "ylt",
                ).toFile()
                val arguments = createCompilation()
                val result = compile(
                    workingDir = tmpDir,
                    arguments = arguments,
                )
                ObjectCacheRegistry.use {
                    val classLoader = URLClassLoader(
                        (arguments.classpath + result.outputClasspath)
                            .mapToArray { it.toURI().toURL() }
                    )
                    LangModelFactory.use(RtModelFactoryImpl(classLoader)) {
                        block(LangModelFactory)
                    }
                }
            }
        }

        private class KspLangTestDriver : LangTestDriverBase() {
            override fun setUpCompilation(
                compilation: TestCompilationArguments,
                block: InspectBlock,
            ) = compilation.copy(
                symbolProcessorProviders = listOf(SymbolProcessorProvider { Inspector(block) })
            )

            private class Inspector(
                private val inspectBlock: InspectBlock,
            ) : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    KspProcessingUtils(resolver).use {
                        LangModelFactory.use(KspModelFactoryImpl()) {
                            inspectBlock(LangModelFactory)
                        }
                    }
                    return emptyList()
                }
            }
        }

        private class JapLangTestDriver : LangTestDriverBase() {
            override fun setUpCompilation(
                compilation: TestCompilationArguments,
                block: InspectBlock,
            ) = compilation.copy(
                kaptProcessors = listOf(Inspector(block)),
            )

            @SupportedSourceVersion(SourceVersion.RELEASE_8)
            private class Inspector(
                private val inspectBlock: InspectBlock,
            ) : AbstractProcessor() {
                private lateinit var types: Types
                private lateinit var elements: Elements

                override fun getSupportedAnnotationTypes() = setOf("*")

                override fun init(processingEnv: ProcessingEnvironment) {
                    super.init(processingEnv)
                    types = processingEnv.typeUtils
                    elements = processingEnv.elementUtils
                }

                override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
                    JapProcessingUtils(types, elements).use {
                        LangModelFactory.use(JavaxModelFactoryImpl()) {
                            inspectBlock(LangModelFactory)
                        }
                        return false
                    }
                }
            }
        }
    }
}


