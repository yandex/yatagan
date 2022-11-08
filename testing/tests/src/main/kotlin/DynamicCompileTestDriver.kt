package com.yandex.yatagan.testing.tests

import com.squareup.javapoet.ClassName
import com.yandex.yatagan.Component
import com.yandex.yatagan.base.ObjectCacheRegistry
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.jap.asTypeElement
import com.yandex.yatagan.lang.jap.isAnnotatedWith
import com.yandex.yatagan.lang.rt.RtModelFactoryImpl
import com.yandex.yatagan.lang.use
import com.yandex.yatagan.processor.common.Logger
import com.yandex.yatagan.processor.common.LoggerDecorator
import org.intellij.lang.annotations.Language
import java.io.File
import java.lang.reflect.Method
import java.util.TreeSet
import java.util.function.Supplier
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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

    override fun runRuntimeTest(test: Method) {
        dynamicRuntimeScope(test.declaringClass.classLoader) {
            super.runRuntimeTest(test)
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
        dynamicRuntimeScope(classLoader) {
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
    }

    private inline fun dynamicRuntimeScope(classLoader: ClassLoader, block: () -> Unit) {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        ObjectCacheRegistry.use {
            LangModelFactory.use(RtModelFactoryImpl(classLoader)) {
                block()
            }
        }
    }

    private class ComponentBootstrapperGenerator : AbstractProcessor() {
        private val _bootstrapperNames: MutableSet<ClassName> = TreeSet()
        val bootstrapperNames: Set<ClassName> get() = _bootstrapperNames

        override fun getSupportedAnnotationTypes() = setOf(Component::class.java.canonicalName)

        override fun getSupportedSourceVersion() = SourceVersion.RELEASE_8

        override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
            for (element: Element in roundEnv.getElementsAnnotatedWith(Component::class.java)) {
                val type = element.asTypeElement()
                if (!type.getAnnotation(Component::class.java).isRoot) continue

                val componentName = ClassName.get(type)
                val builderName = type.enclosedElements.find {
                    it.isAnnotatedWith<Component.Builder>()
                }?.let {
                    componentName.simpleNames().joinToString(".") + '.' + it.simpleName
                }

                val bootstrapperName = ClassName.get(
                    componentName.packageName(),
                    componentName.simpleNames().joinToString("_") + "Bootstrap",
                )
                _bootstrapperNames += bootstrapperName
                val bootstrapInvocation = if (builderName != null) {
                    "builder($builderName.class)"
                } else {
                    "create(${componentName.simpleNames().joinToString(".")}.class)"
                }

                @Language("Java")
                val code = """
                    package ${componentName.packageName()};
                    import com.yandex.yatagan.validation.RichString;
                    import com.yandex.yatagan.*;
                    import java.util.function.*;
                    import java.util.*;
                    public final class ${bootstrapperName.simpleName()} implements Supplier<List<String[]>> {
                        public List<String[]> get() {
                            class InvalidGraph extends RuntimeException {}

                            final List<String[]> log = new ArrayList<>();
                            Yatagan.setMaxIssueEncounterPaths(100);
                            Yatagan.setDynamicValidationDelegate(new DynamicValidationDelegate() {
                                private boolean hasErrors;
                                public boolean getUsePlugins() { return true; }
                                public DynamicValidationDelegate.Promise dispatchValidation(
                                    String title,
                                    DynamicValidationDelegate.Operation operation
                                ) {
                                    operation.validate(new DynamicValidationDelegate.ReportingDelegate() {
                                        public void reportError(RichString message) {
                                            hasErrors = true;
                                            log.add(new String[]{"error", message.toString()});
                                        }
                                        public void reportWarning(RichString message) {
                                            log.add(new String[]{"warning", message.toString()});
                                        }
                                        public void reportMandatoryWarning(RichString message) {
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
                                Yatagan.$bootstrapInvocation;
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