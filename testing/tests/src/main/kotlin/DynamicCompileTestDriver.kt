package com.yandex.yatagan.testing.tests

import com.squareup.javapoet.ClassName
import com.yandex.yatagan.Component
import com.yandex.yatagan.lang.jap.asTypeElement
import com.yandex.yatagan.lang.jap.isAnnotatedWith
import com.yandex.yatagan.processor.common.Logger
import com.yandex.yatagan.processor.common.LoggerDecorator
import com.yandex.yatagan.testing.source_set.SourceFile
import org.intellij.lang.annotations.Language
import java.io.File
import java.lang.reflect.Method
import java.util.TreeSet
import java.util.function.Consumer
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

    private val runnerSource = SourceFile.java("RuntimeTestRunner", """
        import com.yandex.yatagan.Yatagan;
        import java.util.function.*;
        import java.util.*;
        
        public class RuntimeTestRunner implements Consumer<Runnable> {
            @Override
            public void accept(Runnable block) {
                Yatagan.setupReflectionBackend()
                    .useCompiledImplementationIfAvailable(true)
                    .apply();
                try {
                    block.run();
                } finally {
                    Yatagan.resetReflectionBackend();
                }
            }
        }
    """.trimIndent())

    override fun generatedFilesSubDir(): String? = null

    override fun doCompile(): TestCompilationResult {
        val testCompilationResult = super.doCompile()
        check(testCompilationResult.success) {
            "Test source compilation failed, check the code.\n${testCompilationResult.messageLog}"
        }
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
        @Suppress("UNCHECKED_CAST")
        val runner = test.declaringClass.classLoader.loadClass("RuntimeTestRunner")
            .getDeclaredConstructor()
            .newInstance() as Consumer<Runnable>
        runner.accept {
            super.runRuntimeTest(test)
        }
    }

    override fun createCompilationArguments() = super.createCompilationArguments().let {
        it.copy(
            sources = it.sources + runnerSource,
            javacArguments = it.javacArguments + "-parameters",
            kotlincArguments = it.kotlincArguments + "-java-parameters",
            kaptProcessors = listOf(accumulator),
        )
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
                    import com.yandex.yatagan.Yatagan;
                    import com.yandex.yatagan.validation.RichString;
                    import com.yandex.yatagan.rt.support.*;
                    import java.util.function.*;
                    import java.util.*;
                    public final class ${bootstrapperName.simpleName()} implements Supplier<List<String[]>> {
                        public List<String[]> get() {
                            class InvalidGraph extends RuntimeException {}

                            final List<String[]> log = new ArrayList<>();
                            final DynamicValidationDelegate.ReportingDelegate reporting = 
                                new DynamicValidationDelegate.ReportingDelegate() {
                                    @Override public void reportError(RichString message) {
                                        log.add(new String[]{"error", message.toString()});
                                    }
                                    @Override public void reportWarning(RichString message) {
                                        log.add(new String[]{"warning", message.toString()});
                                    }
                                };
                            final Logger logger = new ConsoleLogger("[YataganForTests]");
                            final DynamicValidationDelegate delegate = new SimpleDynamicValidationDelegate(
                                reporting, logger, /*throwOnError*/ true, /*usePlugins*/ true
                            );
                            
                            Yatagan.setupReflectionBackend()
                                .validation(delegate)
                                .maxIssueEncounterPaths(100)
                                .strictMode(true)
                                .useCompiledImplementationIfAvailable(true)
                                .logger(logger)
                                .apply();
                            
                            try {
                                Yatagan.$bootstrapInvocation;
                            } catch (SimpleDynamicValidationDelegate.InvalidGraphException e) {
                                // nothing here
                            } finally {
                                Yatagan.resetReflectionBackend();
                            }
                            return log;
                        }
                    }
                """.trimIndent()
                processingEnv.filer
                    .createSourceFile(bootstrapperName.canonicalName(), type)
                    .openWriter().use { it.write(code) }
            }
            return true
        }
    }
}