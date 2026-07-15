/*
 * Copyright 2026 Yandex LLC
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

package com.yandex.yatagan.kcp

import com.yandex.yatagan.Component
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.util.jar.JarFile
import javax.inject.Inject

class YataganKcpIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `plugin jar registers compiler services`() {
        val registrations = JarFile(pluginJar).use { jar ->
            expectedCompilerServices.keys.associateWith { service ->
                jar.getJarEntry("META-INF/services/$service")?.let { entry ->
                    jar.getInputStream(entry).bufferedReader().use { it.readText().trim() }
                }
            }
        }

        assertThat(registrations).containsExactlyEntriesOf(expectedCompilerServices)
    }

    @Test
    fun `plugin jar packages compiler service implementations`() {
        val entries = JarFile(pluginJar).use { jar ->
            jar.entries().asSequence().map { it.name }.toSet()
        }

        assertThat(entries).contains(
            "com/yandex/yatagan/kcp/YataganCommandLineProcessor.class",
            "com/yandex/yatagan/kcp/YataganCompilerPluginRegistrar.class",
        )
    }

    @Test
    fun `generated top-level component is discovered by Yatagan`() {
        val implementationName = compileAndRun("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Yatagan

            @Component
            interface TestComponent

            fun test(): String {
                val component = Yatagan.create(TestComponent::class.java)
                return component.javaClass.name
            }
        """.trimIndent())

        assertThat(implementationName).isEqualTo("test.YataganTestComponent")
    }

    @Test
    fun `generated component provides a constructor-injected dependency`() {
        val dependencyName = compileAndRun("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Yatagan
            import javax.inject.Inject

            class Dependency @Inject constructor()

            @Component
            interface TestComponent {
                fun dependency(): Dependency
            }

            fun test(): String {
                return Yatagan.create(TestComponent::class.java).dependency().javaClass.name
            }
        """.trimIndent())

        assertThat(dependencyName).isEqualTo("test.Dependency")
    }

    @Test
    fun `generated component calls an object module provision`() {
        val value = compileAndRun("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Yatagan

            @Module
            object TestModule {
                @Provides
                fun provideAny(): Any = "provided"
            }

            @Component(modules = [TestModule::class])
            interface TestComponent {
                fun any(): Any
            }

            fun test(): String {
                return Yatagan.create(TestComponent::class.java).any() as String
            }
        """.trimIndent())

        assertThat(value).isEqualTo("provided")
    }

    @Test
    fun `missing binding is reported through shared decorated diagnostics`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                class Dependency

                @Component
                interface TestComponent {
                    fun dependency(): Dependency
                }
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.usePlainOutput=true",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(">>>[error]\n")
        assertThat(result.messages).contains("Missing binding for test.Dependency")
        assertThat(result.messages).contains("NOTE: No known way to infer the binding")
        assertThat(result.messages).doesNotContain("Unsupported KCP component")
        assertThat(result.outputDirectory.resolve("test/YataganTestComponent.class")).doesNotExist()
    }

    @Test
    fun `warnings are reported alongside validation errors`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component
                import com.yandex.yatagan.Optional

                class ConcreteDependency {
                    fun ignored(): Optional<Any> = Optional.empty()
                }

                class Missing

                @Component(dependencies = [ConcreteDependency::class])
                interface TestComponent {
                    fun missing(): Missing
                }
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.usePlainOutput=true",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains(">>>[error]\n")
        assertThat(result.messages).contains("Missing binding for test.Missing")
        assertThat(result.messages).contains(">>>[warning]\n")
        assertThat(result.messages).contains("Component dependency declaration is not abstract")
        assertThat(result.messages).contains("returns a framework type")
    }

    @Test
    fun `validation-only mode still reports missing bindings`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                class Dependency

                @Component
                interface TestComponent {
                    fun dependency(): Dependency
                }
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.usePlainOutput=true",
                "-P", "plugin:com.yandex.yatagan:yatagan.kcp.codegen=false",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("Missing binding for test.Dependency")
        assertThat(result.outputDirectory.resolve("test/YataganTestComponent.class")).doesNotExist()
    }

    @Test
    fun `unsupported second root prevents emission for every root`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component
                import javax.inject.Inject

                class Dependency @Inject constructor()

                @Component
                interface FirstComponent {
                    fun dependency(): Dependency
                }

                @Component
                interface SecondComponent {
                    fun dependency(): Dependency
                }

                // Collides with the name reserved for the generated implementation.
                class YataganSecondComponent
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.usePlainOutput=true",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("Unsupported KCP component SecondComponent")
        assertThat(result.outputDirectory.resolve("test/YataganFirstComponent.class")).doesNotExist()
    }

    @Test
    fun `generated nested component uses runtime loader name mangling`() {
        val implementationName = compileAndRun("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Yatagan

            class Container {
                @Component
                interface TestComponent
            }

            fun test(): String {
                val component = Yatagan.create(Container.TestComponent::class.java)
                return component.javaClass.name
            }
        """.trimIndent())

        assertThat(implementationName).isEqualTo("test.YataganContainer_TestComponent")
    }

    @Test
    fun `processor options are accepted by the compiler`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                @Component
                interface TestComponent
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.enableStrictMode=false",
            ),
        )

        assertThat(result.exitCode)
            .withFailMessage("Compilation failed:\n%s", result.messages)
            .isEqualTo(ExitCode.OK)
    }

    @Test
    fun `invalid shared boolean option is rejected by the command line processor`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                @Component
                interface TestComponent
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.enableStrictMode=maybe",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("invalid boolean option value: maybe")
    }

    @Test
    fun `invalid shared integer option is rejected by the command line processor`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                @Component
                interface TestComponent
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.maxIssueEncounterPaths=many",
            ),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.messages).contains("invalid integer option value: many")
    }

    @Test
    fun `strict mode false keeps mandatory warnings non-fatal`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Binds
                import com.yandex.yatagan.Component
                import com.yandex.yatagan.Module
                import com.yandex.yatagan.Provides
                import javax.inject.Singleton

                @Module
                interface TestModule {
                    @Binds
                    @Singleton
                    fun number(value: Int): Number

                    companion object {
                        @Provides
                        fun integer(): Int = 0
                    }
                }

                @Component(modules = [TestModule::class])
                @Singleton
                interface TestComponent {
                    fun number(): Number
                }
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.enableStrictMode=false",
                "-P", "plugin:com.yandex.yatagan:yatagan.kcp.codegen=false",
            ),
        )

        assertThat(result.exitCode)
            .withFailMessage("Compilation failed:\n%s", result.messages)
            .isEqualTo(ExitCode.OK)
        assertThat(result.messages).contains("Scope has no effect on 'alias' binding")
    }

    @Test
    fun `validation-only mode does not emit a component implementation`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component

                @Component
                interface TestComponent
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.kcp.codegen=false",
            ),
        )

        assertThat(result.exitCode)
            .withFailMessage("Compilation failed:\n%s", result.messages)
            .isEqualTo(ExitCode.OK)
        assertThat(result.outputDirectory.resolve("test/TestComponent.class")).exists()
        assertThat(result.outputDirectory.resolve("test/YataganTestComponent.class")).doesNotExist()
    }

    @Test
    fun `companion module without JvmStatic requires no instance`() {
        val result = compileAndRun("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Yatagan

            interface Api
            class Impl : Api

            @Module
            interface MyModule {
                companion object {
                    @Provides
                    fun provides(): Api = Impl()

                    @Provides
                    @JvmStatic
                    fun providesLong(): Long = 7L
                }
            }

            @Component(modules = [MyModule::class])
            interface TestComponent {
                fun get(): Api
                fun getLong(): Long
            }

            fun test(): String {
                val c = Yatagan.autoBuilder(TestComponent::class.java).create()
                check(c.get() is Impl)
                check(c.getLong() == 7L)
                return "ok"
            }
        """.trimIndent())
        assertThat(result).isEqualTo("ok")
    }

    @Test
    fun `flattening contribution repro`() {
        val result = compile(
            source = """
                package test

                import com.yandex.yatagan.Component
                import com.yandex.yatagan.IntoList
                import com.yandex.yatagan.Module
                import com.yandex.yatagan.Provides

                @Module
                class TestModuleKotlin {
                    @Provides @IntoList(flatten = true)
                    fun setOfInts(): Set<Int> { throw NotImplementedError() }
                    @Provides @IntoList(flatten = true)
                    fun listOfInts(): List<Int> { throw NotImplementedError() }
                    @Provides @IntoList(flatten = true)
                    fun collectionOfInts(): Collection<Int> { throw NotImplementedError() }
                }

                @Component(modules = [TestModule::class, TestModuleKotlin::class])
                interface TestComponent {
                    val ints: List<Int>
                }
            """.trimIndent(),
            additionalArguments = listOf(
                "-P", "plugin:com.yandex.yatagan:yatagan.usePlainOutput=true",
            ),
            javaSources = mapOf("TestModule" to """
                package test;

                import com.yandex.yatagan.IntoList;
                import com.yandex.yatagan.Module;
                import com.yandex.yatagan.Provides;
                import java.util.Set;
                import java.util.List;
                import java.util.Collection;

                @Module
                public class TestModule {
                    @Provides @IntoList(flatten = true)
                    public static Set<Integer> setOfInts() { return null; }

                    @Provides @IntoList(flatten = true)
                    public List<Integer> listOfInts() { return null; }

                    @Provides @IntoList(flatten = true)
                    public Collection<Integer> collectionOfInts() { return null; }
                }
            """.trimIndent()),
        )
        assertThat(result.messages).doesNotContain("Flattening")
        assertThat(result.exitCode).isEqualTo(ExitCode.OK)
    }

    @Test
    fun `conditional binding repro`() {
        val result = compileAndRun("""
            @file:OptIn(com.yandex.yatagan.ConditionsApi::class)
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Condition
            import com.yandex.yatagan.Conditional
            import com.yandex.yatagan.Optional
            import com.yandex.yatagan.Yatagan
            import javax.inject.Inject

            object Features {
                @get:JvmStatic
                var isEnabledB: Boolean = false
            }

            @Condition(Features::class, condition = "isEnabledB")
            annotation class FeatureB

            @Conditional(FeatureB::class)
            class ClassB @Inject constructor()

            @Component
            interface TestComponent {
                val opt: Optional<ClassB>
            }

            fun test(): String {
                val c1 = Yatagan.create(TestComponent::class.java)
                val r1 = c1.opt.isPresent
                Features.isEnabledB = true
                val c2 = Yatagan.create(TestComponent::class.java)
                val r2 = c2.opt.isPresent
                val r3 = c1.opt.isPresent
                return "r1=" + r1 + " r2=" + r2 + " r3=" + r3
            }
        """.trimIndent())
        assertThat(result).isEqualTo("r1=false r2=true r3=false")
    }

    @Test
    fun `full feature graph compiles and runs`() {
        val result = compileAndRun("""
            @file:OptIn(com.yandex.yatagan.ConditionsApi::class)
            package test

            import com.yandex.yatagan.Binds
            import com.yandex.yatagan.BindsInstance
            import com.yandex.yatagan.Component
            import com.yandex.yatagan.IntoSet
            import com.yandex.yatagan.Lazy
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Optional
            import com.yandex.yatagan.Provides
            import com.yandex.yatagan.Yatagan
            import javax.inject.Inject
            import javax.inject.Named
            import javax.inject.Provider
            import javax.inject.Scope
            import javax.inject.Singleton

            @Scope annotation class SubScope

            class AppInstance(val tag: String)

            @Singleton class ScopedThing @Inject constructor()
            class UnscopedThing @Inject constructor()

            interface Api { val name: String }
            class ApiImpl @Inject constructor() : Api { override val name = "api" }

            class GenericHolder<T : Any> @Inject constructor(val optional: Optional<T>)

            @Module
            object StaticModule {
                @Provides @JvmStatic fun greeting(): String = "hello"
                @Provides @IntoSet @JvmStatic fun one(): Int = 1
                @Provides @IntoSet @JvmStatic fun two(): Int = 2
            }

            @Module
            interface BindsModule {
                @Binds fun api(impl: ApiImpl): Api
            }

            @Module
            class InstanceModule(private val suffix: String) {
                @Provides fun suffix(): CharSequence = suffix
            }

            @Module
            class AutoModule {
                @Provides fun autoValue(): Long = 7L
            }

            class Consumer @Inject constructor(
                val scoped: ScopedThing,
                val scopedLazy: Lazy<ScopedThing>,
                val scopedProvider: Provider<ScopedThing>,
                val unscopedLazy: Lazy<UnscopedThing>,
                val unscopedProvider: Provider<UnscopedThing>,
                val api: Api,
                val ints: Set<Int>,
                val holder: GenericHolder<Api>,
            )

            @SubScope class SubScopedThing @Inject constructor(val fromParent: ScopedThing)

            @Singleton
            @Component(
                modules = [StaticModule::class, BindsModule::class, InstanceModule::class, AutoModule::class],
                multiThreadAccess = true,
            )
            interface RootComponent {
                val scoped: ScopedThing
                val consumer: Consumer
                fun greeting(): String
                fun suffix(): CharSequence
                fun autoValue(): Long
                @Named("count") fun count(): Int
                fun sub(): SubComponent.Builder

                @Component.Builder
                interface Builder {
                    @BindsInstance fun appInstance(instance: AppInstance): Builder
                    @BindsInstance fun count(@Named("count") count: Int): Builder
                    fun instanceModule(module: InstanceModule): Builder
                    fun build(): RootComponent
                }
            }

            @SubScope
            @Component(isRoot = false)
            interface SubComponent {
                val scopedInSub: SubScopedThing
                val fromParent: ScopedThing
                val app: AppInstance
                @Named("sub") fun tag(): String

                @Component.Builder
                interface Builder {
                    fun create(@BindsInstance @Named("sub") tag: String): SubComponent
                }
            }

            fun test(): String {
                val component = Yatagan.builder(RootComponent.Builder::class.java)
                    .appInstance(AppInstance("app"))
                    .count(42)
                    .instanceModule(InstanceModule("-sfx"))
                    .build()

                val consumer = component.consumer
                check(component.scoped === consumer.scoped) { "scoped identity broken" }
                check(consumer.scopedLazy.get() === component.scoped) { "scoped lazy identity broken" }
                check(consumer.scopedProvider.get() === component.scoped) { "scoped provider identity broken" }
                check(consumer.unscopedLazy.get() === consumer.unscopedLazy.get()) { "lazy must cache" }
                check(consumer.unscopedProvider.get() !== consumer.unscopedProvider.get()) {
                    "provider must not cache"
                }
                check(consumer.api is ApiImpl) { "alias broken" }
                check(consumer.api.name == "api") { "api value broken" }
                check(consumer.ints == setOf(1, 2)) { "multibinding broken: " + consumer.ints }
                check(consumer.holder.optional.get() is ApiImpl) { "optional broken" }
                check(component.greeting() == "hello") { "object module provision broken" }
                check(component.suffix() == "-sfx") { "module instance provision broken" }
                check(component.autoValue() == 7L) { "auto-constructed module broken" }
                check(component.count() == 42) { "primitive @BindsInstance broken" }

                val sub = component.sub().create("sub-tag")
                check(sub.tag() == "sub-tag") { "sub factory input broken" }
                check(sub.app.tag == "app") { "parent instance from sub broken" }
                check(sub.fromParent === component.scoped) { "parent scoped from sub broken" }
                check(sub.scopedInSub === sub.scopedInSub) { "sub scoped identity broken" }
                check(sub.scopedInSub.fromParent === component.scoped) { "sub dep on parent scoped broken" }
                val sub2 = component.sub().create("other")
                check(sub2.scopedInSub !== sub.scopedInSub) { "sub instances must not share scoped" }

                return "ok:" + component.javaClass.name
            }
        """.trimIndent())

        assertThat(result).isEqualTo("ok:test.YataganRootComponent")
    }

    private fun compileAndRun(source: String): String {
        val result = compile(source)
        assertThat(result.exitCode)
            .withFailMessage("Compilation failed:\n%s", result.messages)
            .isEqualTo(ExitCode.OK)

        val runtimeClasspath = listOf(result.outputDirectory.absolutePath) +
                testClasspath.split(File.pathSeparatorChar)
        return URLClassLoader(
            runtimeClasspath.map { File(it).toURI().toURL() }.toTypedArray(),
            null,
        ).use { classLoader ->
            classLoader.loadClass("test.TestCaseKt")
                .getDeclaredMethod("test")
                .invoke(null) as String
        }
    }

    private fun compile(
        source: String,
        additionalArguments: List<String> = emptyList(),
        javaSources: Map<String, String> = emptyMap(),
    ): CompilationResult {
        val sourceFile = temporaryFolder.newFile("TestCase.kt").apply {
            writeText(source)
        }
        val javaFiles = javaSources.map { (name, contents) ->
            temporaryFolder.newFile("${'$'}name.java").apply { writeText(contents) }
        }
        val outputDirectory = temporaryFolder.newFolder("classes")
        val compilerOutput = ByteArrayOutputStream()
        val exitCode = PrintStream(compilerOutput).use { output ->
            K2JVMCompiler().exec(
                output,
                "-no-stdlib",
                "-no-reflect",
                "-classpath", testClasspath,
                "-jvm-target", "11",
                "-d", outputDirectory.absolutePath,
                "-Xplugin=${pluginJar.absolutePath}",
                *additionalArguments.toTypedArray(),
                sourceFile.absolutePath,
                *javaFiles.map { it.absolutePath }.toTypedArray(),
            )
        }
        val messages = compilerOutput.toString(Charsets.UTF_8.name())
        return CompilationResult(exitCode, messages, outputDirectory)
    }

    private data class CompilationResult(
        val exitCode: ExitCode,
        val messages: String,
        val outputDirectory: File,
    )

    private companion object {
        val expectedCompilerServices = mapOf(
            "org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor" to
                    "com.yandex.yatagan.kcp.YataganCommandLineProcessor",
            "org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar" to
                    "com.yandex.yatagan.kcp.YataganCompilerPluginRegistrar",
        )

        val pluginJar: File
            get() = File(checkNotNull(System.getProperty("com.yandex.yatagan.kcp.pluginJar")))

        val testClasspath: String
            get() = listOf(
                Component::class.java,
                Unit::class.java,
                Inject::class.java,
            ).map { type ->
                File(type.protectionDomain.codeSource.location.toURI()).absolutePath
            }.distinct().joinToString(File.pathSeparator)
    }
}
