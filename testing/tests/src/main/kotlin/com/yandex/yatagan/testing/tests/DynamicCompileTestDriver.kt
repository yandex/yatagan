/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.testing.tests

import com.squareup.javapoet.ClassName
import com.yandex.yatagan.Component
import com.yandex.yatagan.lang.jap.asTypeElement
import com.yandex.yatagan.lang.jap.isAnnotatedWith
import com.yandex.yatagan.processor.common.IntOption
import com.yandex.yatagan.processor.common.Logger
import com.yandex.yatagan.processor.common.LoggerDecorator
import com.yandex.yatagan.processor.common.Option
import com.yandex.yatagan.testing.source_set.SourceFile
import org.intellij.lang.annotations.Language
import java.io.File
import java.lang.reflect.Method
import java.util.Properties
import java.util.TreeSet
import java.util.function.Consumer
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

private const val TEST_DELEGATE = "test.TestValidationDelegate"
private const val TEST_RUNNER = "test.RuntimeTestRunner"

class DynamicCompileTestDriver(
    apiType: ApiType = ApiType.Dynamic,
) : CompileTestDriverBase(apiType) {
    private val accumulator = ComponentBootstrapperGenerator()
    private val options = mutableMapOf<Option<*>, Any>(
        IntOption.MaxIssueEncounterPaths to 100,
    )

    private val runnerSource = SourceFile.java(TEST_RUNNER, """
        package test;
        import com.yandex.yatagan.dynamic.YataganReflection;
        import java.util.function.Consumer;
        
        public class RuntimeTestRunner implements Consumer<Runnable> {
            @Override
            public void accept(Runnable block) {
                try {
                    block.run();
                } finally {
                    YataganReflection.resetGlobalState();
                }
            }
        }
    """.trimIndent())

    private val validationDelegateSource = SourceFile.java(TEST_DELEGATE, """
        package test;
        import com.yandex.yatagan.rt.support.ConsoleLogger;
        import com.yandex.yatagan.rt.support.DynamicValidationDelegate;
        import com.yandex.yatagan.rt.support.SimpleDynamicValidationDelegate;
        import com.yandex.yatagan.validation.RichString;
        import java.util.List;
        import java.util.ArrayList;

        public class TestValidationDelegate extends SimpleDynamicValidationDelegate {
            public static final List<String[]> sLog = new ArrayList<>();
            public TestValidationDelegate() {
                super(new DynamicValidationDelegate.ReportingDelegate() {
                    @Override public void reportError(String message) {
                        sLog.add(new String[]{"e", message});
                    }
                    @Override public void reportWarning(String message) {
                        sLog.add(new String[]{"w", message});
                    }
                }, new ConsoleLogger("[YataganForTests]"), true, true);
            }
        }
    """.trimIndent())

    override val checkGoldenOutput: Boolean
        get() = false

    override fun generatedFilesSubDir(): String? = null

    override fun <V : Any> givenOption(option: Option<V>, value: V) {
        options[option] = value
    }

    override fun doCompile(): TestCompilationResult {
        val testCompilationResult = super.doCompile()
        check(testCompilationResult.success) {
            "Test source compilation failed, check the code.\n${testCompilationResult.messageLog}"
        }
        val log = StringBuilder()
        val runtimeValidationSuccess = validateRuntimeComponents(
            workingDir = testCompilationResult.workingDir,
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
        val runner = test.declaringClass.classLoader.loadClass(TEST_RUNNER)
            .getDeclaredConstructor()
            .newInstance() as Consumer<Runnable>
        runner.accept {
            super.runRuntimeTest(test)
        }
    }

    override fun createCompilationArguments() = super.createCompilationArguments().let {
        it.copy(
            sources = it.sources + arrayOf(runnerSource, validationDelegateSource),
            kaptProcessors = listOf(accumulator),
        )
    }

    override val backendUnderTest: Backend
        get() = Backend.Rt

    private fun validateRuntimeComponents(
        workingDir: File,
        componentBootstrapperNames: Set<ClassName>,
        runtimeClasspath: List<File>,
        log: StringBuilder,
    ): Boolean {
        val resourcesDir = workingDir.resolve("resources")
        val propertiesFile = resourcesDir.resolve("META-INF/com.yandex.yatagan.reflection/parameters.properties")
        propertiesFile.parentFile.mkdirs()
        propertiesFile.bufferedWriter().use {
            Properties().apply {
                put("validationDelegateClass", TEST_DELEGATE)
                for ((option, value) in options) {
                    put(option.key.substringAfterLast('.'), value.toString())
                }
            }.store(it, "Generated test properties")
        }

        val classLoader = makeClassLoader(runtimeClasspath + resourcesDir)
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
            val bootstrapper = classLoader.loadClass(bootstrapperName.reflectionName())
                .getDeclaredConstructor()
                .newInstance() as Runnable

            bootstrapper.run()
        }

        val delegateLog = classLoader.loadClass(TEST_DELEGATE)
            .getDeclaredField("sLog")
            .get(null)

        @Suppress("UNCHECKED_CAST")
        for ((kind, message) in delegateLog as List<Array<String>>) {
            when (kind) {
                "e" -> logger.error(message)
                "w" -> logger.warning(message)
                else -> throw AssertionError("not reached")
            }
        }
        return success
    }

    private inner class ComponentBootstrapperGenerator : AbstractProcessor() {
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
                    "autoBuilder(${componentName.simpleNames().joinToString(".")}.class)"
                }

                @Language("Java")
                val code = """
                    package ${componentName.packageName()};
                    import com.yandex.yatagan.Yatagan;
                    import com.yandex.yatagan.dynamic.YataganReflection;
                    import com.yandex.yatagan.validation.RichString;
                    import com.yandex.yatagan.rt.support.*;
                    import java.util.*;
                    
                    public final class ${bootstrapperName.simpleName()} implements Runnable {
                        public void run() {
                            try {
                                Yatagan.$bootstrapInvocation;
                            } catch (SimpleDynamicValidationDelegate.InvalidGraphException e) {
                                // nothing here
                            } finally {
                                YataganReflection.resetGlobalState();
                            }
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