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

@file:OptIn(
    org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class,
    org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class,
)

package com.yandex.yatagan.lang.kcp

import com.yandex.yatagan.Component
import com.yandex.yatagan.lang.getCollectionType
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import javax.inject.Inject

class KcpJavaSourceDebugTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `java source class members are visible through lang models`() {
        val javaFile = temporaryFolder.newFile("MyFeature.java").apply {
            writeText("""
                package test;

                import java.util.List;
                import java.util.Set;

                class PkgPrivate {
                    public boolean flag = false;
                }

                public class MyFeature {
                    static boolean sEnabled = false;
                    public static final MyFeature INSTANCE = new MyFeature();
                    boolean isEnabled() { return sEnabled; }
                    public static List<Integer> listOfInts() { return null; }
                    public static List rawList() { return null; }
                    public static Set<? extends CharSequence> bars() { return null; }
                }
            """.trimIndent())
        }
        val kotlinFile = temporaryFolder.newFile("TestCase.kt").apply {
            writeText("""
                package test

                class KotlinModule {
                    fun listOfInts(): List<Int> { throw NotImplementedError() }
                }
            """.trimIndent())
        }
        val outputDirectory = temporaryFolder.newFolder("classes")
        val probeFile = temporaryFolder.newFile("java-probe.txt")
        val compilerOutput = ByteArrayOutputStream()
        System.setProperty(JavaProbeFileProperty, probeFile.absolutePath)
        val exitCode = try {
            PrintStream(compilerOutput).use { output ->
                K2JVMCompiler().exec(
                    output,
                    "-no-stdlib",
                    "-no-reflect",
                    "-classpath", testClasspath,
                    "-jvm-target", "11",
                    "-d", outputDirectory.absolutePath,
                    "-Xplugin=${testPluginJar.absolutePath}",
                    kotlinFile.absolutePath,
                    javaFile.absolutePath,
                )
            }
        } finally {
            System.clearProperty(JavaProbeFileProperty)
        }
        assertThat(exitCode)
            .withFailMessage("Compilation failed:\n%s", compilerOutput.toString(Charsets.UTF_8.name()))
            .isEqualTo(ExitCode.OK)
        println(probeFile.readText())
        assertThat(probeFile.readText())
            .contains("field:sEnabled static=true")
            .contains("method:isEnabled static=false")
    }

    private companion object {
        val testPluginJar: File
            get() = File(checkNotNull(
                System.getProperty("com.yandex.yatagan.lang.kcp.testPluginJar"),
            ))

        val testClasspath: String
            get() = listOf(Component::class.java, Inject::class.java, Unit::class.java)
                .map { type -> File(type.protectionDomain.codeSource.location.toURI()).absolutePath }
                .distinct()
                .joinToString(File.pathSeparator)
    }
}

class KcpJavaSourceDebugRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "com.yandex.yatagan.lang.kcp.java-debug"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(object : IrGenerationExtension {
            override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
                val probePath = System.getProperty(JavaProbeFileProperty) ?: return
                val scope = KcpLexicalScope(pluginContext, moduleFragment.files.first())
                val symbol = scope.finder.findClass(ClassId.topLevel(FqName("test.MyFeature")))
                    ?: run { File(probePath).writeText("class not found"); return }
                val declaration = scope.getTypeDeclaration(symbol.owner)
                File(probePath).writeText(buildString {
                    appendLine("origin=${symbol.owner.origin}")
                    declaration.fields.forEach { appendLine("field:${it.name} static=${it.isStatic}") }
                    declaration.methods.forEach { appendLine("method:${it.name} static=${it.isStatic}") }
                    declaration.constructors.forEach { appendLine("constructor params=${it.parameters.count()}") }
                    val factory = scope.ext[com.yandex.yatagan.lang.LangModelFactory]
                    val listOfInts = declaration.methods.first { it.name == "listOfInts" }
                    val elementType = listOfInts.returnType.typeArguments.firstOrNull()
                    appendLine("listOfInts.returnType=${listOfInts.returnType}")
                    appendLine("listOfInts.element=$elementType")
                    if (elementType != null) {
                        val collection = factory.getCollectionType(elementType)
                        appendLine("collectionType=$collection")
                        appendLine("assignable=${collection.isAssignableFrom(listOfInts.returnType)}")
                    }
                    val rawList = declaration.methods.first { it.name == "rawList" }
                    appendLine("rawList.returnType=${rawList.returnType}")
                    val bars = declaration.methods.first { it.name == "bars" }
                    appendLine("bars.returnType=${bars.returnType}")
                    val kotlinClass = scope.finder.findClass(ClassId.topLevel(FqName("test.KotlinModule")))!!
                    val kotlinDeclaration = scope.getTypeDeclaration(kotlinClass.owner)
                    val kListOfInts = kotlinDeclaration.methods.first { it.name == "listOfInts" }
                    appendLine("k.listOfInts.returnType=${kListOfInts.returnType}")
                    appendLine("sameInstance=${kListOfInts.returnType === listOfInts.returnType}")
                    val kElement = kListOfInts.returnType.typeArguments.first()
                    val kCollection = factory.getCollectionType(kElement)
                    appendLine("k.collectionType=$kCollection")
                    appendLine("k.assignable=${kCollection.isAssignableFrom(kListOfInts.returnType)}")
                    val pkgPrivate = scope.finder.findClass(ClassId.topLevel(FqName("test.PkgPrivate")))
                    if (pkgPrivate != null) {
                        val decl = scope.getTypeDeclaration(pkgPrivate.owner)
                        appendLine("pkgPrivate.isEffectivelyPublic=${decl.isEffectivelyPublic}")
                        appendLine("pkgPrivate.visibility=${pkgPrivate.owner.visibility}")
                    } else appendLine("pkgPrivate=not found")
                })
            }
        })
    }
}

internal const val JavaProbeFileProperty = "com.yandex.yatagan.lang.kcp.javaProbeFile"
