package com.yandex.daggerlite.core.lang

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.jap.lang.JavaxModelFactoryImpl
import com.yandex.daggerlite.ksp.lang.KspModelFactoryImpl
import com.yandex.daggerlite.lang.rt.RtModelFactoryImpl
import com.yandex.daggerlite.testing.SourceSet
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

typealias InspectBlock = LangModelFactory.() -> Unit

private typealias JapProcessingUtils = com.yandex.daggerlite.jap.lang.ProcessingUtils
private typealias KspProcessingUtils = com.yandex.daggerlite.ksp.lang.ProcessingUtils

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
            protected fun createCompilation(): KotlinCompilation {
                return KotlinCompilation().apply {
                    sources = sourceFiles
                    verbose = false
                    inheritClassPath = false
                    javacArguments += "-Xdiags:verbose"
                }
            }

            protected open fun setUpCompilation(compilation: KotlinCompilation, block: InspectBlock) {}

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
                    val result = compilation.compile()

                    error?.let { throw it }

                    check(result.exitCode == KotlinCompilation.ExitCode.OK) {
                        "Lang test should compile, yet the compilation failed with ${result.exitCode}"
                    }
                }
            }
        }

        private class RtLangTestDriver : LangTestDriverBase() {
            override fun inspect(block: InspectBlock) {
                val result = createCompilation().compile()
                ObjectCacheRegistry.use {
                    LangModelFactory.use(RtModelFactoryImpl(result.classLoader)) {
                        block(LangModelFactory)
                    }
                }
            }
        }

        private class KspLangTestDriver : LangTestDriverBase() {
            override fun setUpCompilation(compilation: KotlinCompilation, block: InspectBlock) {
                compilation.symbolProcessorProviders = listOf(SymbolProcessorProvider { Inspector(block) })
            }

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
            override fun setUpCompilation(compilation: KotlinCompilation, block: InspectBlock) {
                compilation.annotationProcessors = listOf(Inspector(block))
            }

            @Suppress("Since15")
            @SupportedSourceVersion(SourceVersion.RELEASE_11)
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


