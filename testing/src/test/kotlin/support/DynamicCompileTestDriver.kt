package com.yandex.daggerlite.testing.support

import com.tschuchort.compiletesting.KotlinCompilation
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.use
import com.yandex.daggerlite.graph.impl.BindingGraph
import com.yandex.daggerlite.lang.rt.RtModelFactoryImpl
import com.yandex.daggerlite.lang.rt.TypeDeclarationLangModel
import com.yandex.daggerlite.process.Logger
import com.yandex.daggerlite.process.LoggerDecorator
import com.yandex.daggerlite.validation.ValidationMessage
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.validate
import java.io.File
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

class DynamicCompileTestDriver : CompileTestDriverBase(apiType = ApiType.Dynamic) {
    override fun doValidate(): ValidationResult {
        val accumulator = ComponentAccumulator()
        val compilation = createKotlinCompilation().apply {
            sources = sourceFiles
            annotationProcessors = listOf(accumulator)
        }
        val result = compilation.compile()
        if (result.exitCode != KotlinCompilation.ExitCode.OK) {
            throw RuntimeException("Test source compilation failed, check the code")
        }
        val runtimeClasspath = compilation.classpaths + compilation.classesDir
        val log = StringBuilder()
        val success = validateRuntimeComponents(
            componentNames = accumulator.componentNames,
            runtimeClasspath = runtimeClasspath,
            log = log,
        )
        return ValidationResult(
            runtimeClasspath = runtimeClasspath,
            messageLog = log.toString(),
            success = success,
            generatedFiles = emptyList(),
        )
    }

    override fun expectValidationResults(vararg messages: Message) {
        dynamicRuntimeScope {
            super.expectValidationResults(*messages)
        }
    }

    override fun createKotlinCompilation(): KotlinCompilation {
        return super.createKotlinCompilation().apply {
            javacArguments += "-parameters"
            javaParameters = true
        }
    }

    override val backendUnderTest: Backend
        get() = Backend.Rt

    private fun validateRuntimeComponents(
        componentNames: Set<String>,
        runtimeClasspath: List<File>,
        log: StringBuilder,
    ): Boolean {
        val classLoader = makeClassLoader(runtimeClasspath)
        val graphs = buildList {
            for (componentName in componentNames) {
                val componentClass = classLoader.loadClass(componentName)
                val componentDeclaration = TypeDeclarationLangModel(componentClass)
                val componentModel = ComponentModel(componentDeclaration)
                if (!componentModel.isRoot) {
                    continue
                }
                val graph = BindingGraph(root = componentModel)
                add(graph)
            }
        }
        var success = true
        val logger = LoggerDecorator(object : Logger {
            override fun error(message: String) {
                success = false
                log.appendLine(message)
                println(message)
            }

            override fun warning(message: String) {
                log.appendLine(message)
                println(message)
            }
        })
        for (message in validate(graphs)) {
            when (message.message.kind) {
                ValidationMessage.Kind.Error -> logger.error(Strings.formatMessage(
                    message = message.message.contents,
                    color = Strings.StringColor.Red,
                    encounterPaths = message.encounterPaths,
                    notes = message.message.notes,
                ))
                ValidationMessage.Kind.Warning -> logger.warning(Strings.formatMessage(
                    message = message.message.contents,
                    color = Strings.StringColor.Yellow,
                    encounterPaths = message.encounterPaths,
                    notes = message.message.notes,
                ))
            }
        }
        return success
    }

    private inline fun dynamicRuntimeScope(block: () -> Unit) {
        ObjectCacheRegistry.use {
            LangModelFactory.use(RtModelFactoryImpl()) {
                block()
            }
        }
    }

    private class ComponentAccumulator : AbstractProcessor() {
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
}