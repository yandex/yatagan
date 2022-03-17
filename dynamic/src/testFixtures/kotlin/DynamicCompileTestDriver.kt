package com.yandex.daggerlite.dynamic

import com.tschuchort.compiletesting.KotlinCompilation
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.core.lang.InternalLangApi
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.use
import com.yandex.daggerlite.graph.impl.BindingGraph
import com.yandex.daggerlite.lang.rt.RtModelFactoryImpl
import com.yandex.daggerlite.lang.rt.TypeDeclarationLangModel
import com.yandex.daggerlite.testing.CompileTestDriver
import com.yandex.daggerlite.testing.CompileTestDriverBase
import com.yandex.daggerlite.validation.ValidationMessage
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.validate
import java.util.TreeSet
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementVisitor
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.TypeParameterElement
import javax.lang.model.element.VariableElement
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalLangApi::class)
class DynamicCompileTestDriver : CompileTestDriverBase(apiType = ApiType.Dynamic) {
    override fun failsToCompile(block: CompileTestDriver.CompilationResultClause.() -> Unit) {
        runTest {
            val accumulator = ComponentAccumulator()
            val compilation = setupCompilation(accumulator)
            val result = compilation.compile()
            assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK,
                "Invalid test source - compilation failed")

            assertFalse {
                RtCompilationResultClause(
                    accumulator = accumulator,
                    result = result,
                    compiledClassesLoader = makeClassLoader(compilation),
                ).apply(block)
                    .validationSuccessful
            }
        }
    }

    override fun compilesSuccessfully(block: CompileTestDriver.CompilationResultClause.() -> Unit) {
        runTest {
            val accumulator = ComponentAccumulator()
            val compilation = setupCompilation(accumulator)
            val result = compilation.compile()
            assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK,
                "Invalid test source - compilation failed")

            assertTrue {
                RtCompilationResultClause(
                    accumulator = accumulator,
                    result = result,
                    compiledClassesLoader = makeClassLoader(compilation),
                ).apply(block)
                    .validationSuccessful
            }
        }
    }

    private fun setupCompilation(accumulator: ComponentAccumulator) = KotlinCompilation().apply {
        precompileIfNeeded()?.let { precompiled ->
            classpaths = classpaths + precompiled
        }
        basicKotlinCompilationSetup()
        sources = sourceFiles
        annotationProcessors = listOf(accumulator)
    }

    private inline fun runTest(block: () -> Unit) {
        ObjectCacheRegistry.use {
            try {
                block()
            } finally {
                LangModelFactory.delegate = null
            }
        }
    }

    private inner class ComponentAccumulator : AbstractProcessor() {
        private val _componentNames: MutableSet<String> = TreeSet()
        val componentNames: Set<String> get() = _componentNames

        override fun getSupportedAnnotationTypes() = setOf(Component::class.java.canonicalName)

        @Suppress("Since15")
        override fun getSupportedSourceVersion() = SourceVersion.RELEASE_11

        override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
            for (element: Element in roundEnv.getElementsAnnotatedWith(Component::class.java)) {
                val type = element.accept(object : ElementVisitor<TypeElement?, Unit> {
                    override fun visitType(e: TypeElement, p: Unit) = e
                    override fun visit(e: Element, p: Unit) = null
                    override fun visitPackage(e: PackageElement, p: Unit) = visit(e, p)
                    override fun visitVariable(e: VariableElement, p: Unit) = visit(e, p)
                    override fun visitExecutable(e: ExecutableElement, p: Unit) = visit(e, p)
                    override fun visitTypeParameter(e: TypeParameterElement, p: Unit) = visit(e, p)
                    override fun visitUnknown(e: Element, p: Unit) = visit(e, p)
                }, Unit) ?: continue
                _componentNames += type.qualifiedName.toString()
            }
            return true
        }
    }

    private class RtCompilationResultClause(
        accumulator: ComponentAccumulator,
        result: KotlinCompilation.Result,
        compiledClassesLoader: ClassLoader,
    ) : CompilationResultClauseBase(result, compiledClassesLoader) {

        private val messages: List<Message>

        init {
            LangModelFactory.use(RtModelFactoryImpl()) {
                val graphs = buildList {
                    for (componentName in accumulator.componentNames) {
                        val componentClass = compiledClassesLoader.loadClass(componentName)
                        val componentDeclaration = TypeDeclarationLangModel(componentClass)
                        val componentModel = ComponentModel(componentDeclaration)
                        if (!componentModel.isRoot) {
                            continue
                        }
                        val graph = BindingGraph(root = componentModel)
                        add(graph)
                    }
                }
                messages = validate(graphs).map {
                    Message(
                        kind = when (it.message.kind) {
                            ValidationMessage.Kind.Error -> Message.Kind.Error
                            ValidationMessage.Kind.Warning -> Message.Kind.Warning
                        },
                        text = Strings.formatMessage(
                            message = it.message.contents,
                            encounterPaths = it.encounterPaths,
                            notes = it.message.notes,
                        )
                    )
                }
            }
            for (message in messages) {
                println("[rt] $message")
                println()
            }
        }

        val validationSuccessful: Boolean
            get() = messages.none { it.kind == Message.Kind.Error }

        override fun populateMessages(): Sequence<Message> {
            return messages.asSequence()
        }

        override fun generatesJavaSources(name: String) {
            // Nothing here, as no generation is done
        }
    }

    override val backendUnderTest: CompileTestDriver.Backend
        get() = CompileTestDriver.Backend.Rt
}