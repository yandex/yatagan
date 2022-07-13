package com.yandex.daggerlite.testing

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.Component
import com.yandex.daggerlite.base.ObjectCacheRegistry
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.use
import com.yandex.daggerlite.jap.lang.asTypeElement
import com.yandex.daggerlite.jap.lang.isAnnotatedWith
import com.yandex.daggerlite.lang.rt.RtModelFactoryImpl
import com.yandex.daggerlite.process.Logger
import com.yandex.daggerlite.process.LoggerDecorator
import org.intellij.lang.annotations.Language
import java.io.File
import java.util.TreeSet
import java.util.function.Supplier
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

class DynamicCompileTestDriver(
    apiType: ApiType = ApiType.Dynamic,
) : CompileTestDriverBase(apiType) {
    private val accumulator = ComponentBootstrapperGenerator()

    override fun doCompile(): TestCompilationResult {
        val testCompilationResult = super.doCompile()
        check(testCompilationResult.success) { "Test source compilation failed, check the code" }
        val log = StringBuilder()
        val runtimeValidationSuccess = validateRuntimeComponents(
            componentBootstrapperNames = accumulator.bootstrapperNames,
            runtimeClasspath = testCompilationResult.runtimeClasspath,
            log = log,
        )
        return testCompilationResult.copy(
            success = runtimeValidationSuccess,
            messageLog = log.toString(),
        )
    }

    override fun expectValidationResults(vararg messages: Message) {
        dynamicRuntimeScope {
            super.expectValidationResults(*messages)
        }
    }

    override fun createKotlinCompilation() = super.createKotlinCompilation().apply {
        sources = sourceFiles
        javacArguments += "-parameters"
        javaParameters = true
        annotationProcessors = listOf(accumulator)
    }

    override val backendUnderTest: Backend
        get() = Backend.Rt

    private fun validateRuntimeComponents(
        componentBootstrapperNames: Set<ClassName>,
        runtimeClasspath: List<File>,
        log: StringBuilder,
    ): Boolean {
        val classLoader = makeClassLoader(runtimeClasspath)
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
        for (bootstrapperName in componentBootstrapperNames) {
            @Suppress("UNCHECKED_CAST")
            val bootstrapper = classLoader.loadClass(bootstrapperName.reflectionName())
                .getDeclaredConstructor()
                .newInstance() as Supplier<List<Array<String>>>
            for ((kind, message) in bootstrapper.get()) {
                when (kind) {
                    "error" -> logger.error(message)
                    "warning" -> logger.warning(message)
                    else -> throw AssertionError("not reached")
                }
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

    private class ComponentBootstrapperGenerator : AbstractProcessor() {
        private val _bootstrapperNames: MutableSet<ClassName> = TreeSet()
        val bootstrapperNames: Set<ClassName> get() = _bootstrapperNames

        override fun getSupportedAnnotationTypes() = setOf(Component::class.java.canonicalName)

        @Suppress("Since15")
        override fun getSupportedSourceVersion() = SourceVersion.RELEASE_11

        override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
            for (element: Element in roundEnv.getElementsAnnotatedWith(Component::class.java)) {
                val type = element.asTypeElement()
                if (!type.getAnnotation(Component::class.java).isRoot) continue

                val componentName = ClassName.get(type)
                val builderName = type.enclosedElements.find {
                    it.isAnnotatedWith<Component.Builder>()
                }?.let { "${componentName.simpleName()}.${it.simpleName}" }

                val bootstrapperName = componentName.peerClass("${componentName.simpleName()}Bootstrap")
                _bootstrapperNames += bootstrapperName
                val bootstrapInvocation = if (builderName != null) {
                    "builder($builderName.class)"
                } else {
                    "create(${componentName.simpleName()}.class)"
                }

                @Language("Java")
                val code = """
                    package ${componentName.packageName()};
                    import com.yandex.daggerlite.*;
                    import java.util.function.*;
                    import java.util.*;
                    public final class ${componentName.simpleName()}Bootstrap implements Supplier<List<String[]>> {
                        public List<String[]> get() {
                            class InvalidGraph extends RuntimeException {}

                            final List<String[]> log = new ArrayList<>();
                            Dagger.setDynamicValidationDelegate(new DynamicValidationDelegate() {
                                private boolean hasErrors;
                                public boolean getUsePlugins() { return true; }
                                public DynamicValidationDelegate.Promise dispatchValidation(
                                    String title,
                                    DynamicValidationDelegate.Operation operation
                                ) {
                                    operation.validate(new DynamicValidationDelegate.ReportingDelegate() {
                                        public void reportError(String message) {
                                            hasErrors = true;
                                            log.add(new String[]{"error", message});
                                        }
                                        public void reportWarning(String message) {
                                            log.add(new String[]{"warning", message});
                                        }
                                        public void reportMandatoryWarning(String message) {
                                            // Strict mode
                                            reportError(message);
                                        }
                                    });
                                    if (hasErrors) {
                                        throw new InvalidGraph();
                                    }
                                    return null;
                                }
                            });
                            try {
                                Dagger.$bootstrapInvocation;
                            } catch (InvalidGraph e) {/*nothing here*/}
                            return log;
                        }
                    }
                """.trimIndent()
                processingEnv.filer
                    .createSourceFile(bootstrapperName.canonicalName(), type)
                    .openWriter().buffered().use { it.write(code) }
            }
            return true
        }
    }
}