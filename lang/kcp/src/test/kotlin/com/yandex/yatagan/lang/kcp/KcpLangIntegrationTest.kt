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

@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.lang.kcp

import com.yandex.yatagan.Component
import com.yandex.yatagan.base.api.Internal
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.langFactory
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.file
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class KcpLangIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `component defaults are available through IR lang models`() {
        val result = compileAndProbe("""
            package test

            import com.yandex.yatagan.Component

            @Component
            interface Root
        """.trimIndent())

        assertThat(result).containsExactlyEntriesOf(mapOf(
            "qualifiedName" to "test.Root",
            "kind" to "Interface",
            "isAbstract" to "true",
            "isEffectivelyPublic" to "true",
            "type" to "test.Root",
            "annotationClass" to "com.yandex.yatagan.Component",
            "isRoot" to "true",
            "modules" to "[]",
            "dependencies" to "[]",
            "variant" to "[]",
            "multiThreadAccess" to "false",
            "factoryLookupIsCached" to "true",
            "platformModel" to "Root",
            "referenceNullabilityIsErased" to "true",
            "primitiveBoxingIsDistinct" to "true",
            "primitiveType" to "int",
            "boxedType" to "java.lang.Integer",
            "asBoxedIsCached" to "true",
            "genericNullabilityIsErased" to "true",
            "genericType" to "java.util.List<java.lang.String>",
        ))
    }

    @Test
    fun `source annotation values are exposed through the lang visitor`() {
        val result = compileAndProbe("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Module
            import kotlin.reflect.KClass

            enum class Mode { Fast }
            annotation class Nested(val value: String)
            annotation class Config(
                val enabled: Boolean = true,
                val count: Int = 7,
                val text: String = "value",
                val type: KClass<*> = String::class,
                val mode: Mode = Mode.Fast,
                val nested: Nested = Nested("inside"),
                val types: Array<KClass<*>> = [Int::class, String::class],
            )

            @Module
            object TestModule
            interface Dependency

            @Component(
                isRoot = false,
                modules = [TestModule::class],
                dependencies = [Dependency::class],
                multiThreadAccess = true,
            )
            @Config(enabled = false, text = "explicit", nested = Nested("outside"))
            interface Root
        """.trimIndent())

        assertThat(result).containsAllEntriesOf(mapOf(
            "isRoot" to "false",
            "modules" to "[test.TestModule]",
            "dependencies" to "[test.Dependency]",
            "multiThreadAccess" to "true",
            "config.enabled" to "boolean:false",
            "config.count" to "int:7",
            "config.text" to "string:explicit",
            "config.type" to "type:java.lang.String",
            "config.mode" to "enum:test.Mode.Fast",
            "config.nested" to "annotation:@test.Nested(value=\"outside\")",
            "config.types" to "array:[type:int, type:java.lang.String]",
        ))
    }

    @Test
    fun `IR callables expose the Java view needed by the binding graph`() {
        val result = compileAndProbe("""
            package test

            import com.yandex.yatagan.Component
            import com.yandex.yatagan.Module
            import com.yandex.yatagan.Provides
            import javax.inject.Inject
            import javax.inject.Named

            class Dependency

            class Subject @Inject internal constructor(
                @Named("ctor") val dependency: Dependency,
            )

            open class BaseModule {
                @Provides
                open fun inherited(@Named("base") input: String): Long = 1L

                protected fun visibleProtected() = Unit
                private fun hidden() = Unit
            }

            @Module
            object TestModule : BaseModule() {
                @Provides
                fun provide(@Named("method") input: String): Dependency = Dependency()

                @get:Provides
                @get:Named("property")
                val count: Int get() = 1

                @JvmStatic
                @Provides
                fun staticText(): String = ""

                @JvmField
                val exposed: String = ""

                lateinit var late: Dependency
            }

            @Component(modules = [TestModule::class])
            interface Root {
                fun dependency(): Dependency
            }
        """.trimIndent())

        assertThat(result).containsAllEntriesOf(mapOf(
            "subject.constructor.count" to "1",
            "subject.constructor.inject" to "true",
            "subject.constructor.public" to "true",
            "subject.constructor.parameter" to "dependency:test.Dependency",
            "subject.constructor.parameterAnnotations" to "[javax.inject.Named]",
            "subject.constructor.platformModel" to "true",
            "module.kind" to "KotlinObject",
            "module.methods" to "[getCount, getLate, inherited, provide, setLate, staticText, visibleProtected]",
            "module.provide.owner" to "test.TestModule",
            "module.provide.provides" to "true",
            "module.provide.parameter" to "input:java.lang.String",
            "module.provide.parameterAnnotations" to "[javax.inject.Named]",
            "module.provide.static" to "false",
            "module.provide.platformModel" to "true",
            "module.getCount.annotations" to "[com.yandex.yatagan.Provides, javax.inject.Named]",
            "module.getCount.returnType" to "int",
            "module.getCount.provides" to "true",
            "module.getCount.static" to "false",
            "module.getCount.platformModel" to "true",
            "module.inherited.owner" to "test.TestModule",
            "module.inherited.provides" to "true",
            "module.inherited.returnType" to "long",
            "module.staticText.static" to "true",
            "module.visibleProtected.public" to "false",
            "module.fields" to "[INSTANCE, exposed, late]",
            "module.field.exposed" to "java.lang.String:true:true",
            "module.field.late" to "test.Dependency:true:true",
            "module.field.INSTANCE" to "test.TestModule:true:false",
            "root.methods" to "[dependency]",
            "root.dependency.abstract" to "true",
            "root.dependency.owner" to "test.Root",
            "root.dependency.platformModel" to "true",
        ))
    }

    @Test
    fun `Kotlin types with the same JVM view share type identity`() {
        val result = compileAndProbe("""
            package test

            import com.yandex.yatagan.Component
            import kotlin.reflect.KClass

            interface Shapes {
                fun readOnly(): List<Number>
                fun mutable(): MutableList<Number>
                fun kotlinClass(): KClass<*>
                fun javaClass(): Class<*>
                fun function0(): () -> Unit
                fun function1(): String.() -> Int
                fun function3(): String.(Long, Long) -> ByteArray
            }

            @Component
            interface Root
        """.trimIndent())

        assertThat(result).containsAllEntriesOf(mapOf(
            "shapes.listTypes" to "java.util.List<java.lang.Number>:java.util.List<java.lang.Number>",
            "shapes.listIdentity" to "true",
            "shapes.classTypes" to "java.lang.Class<?>:java.lang.Class<?>",
            "shapes.classIdentity" to "true",
            "shapes.functionDeclarations" to "kotlin.jvm.functions.Function0:" +
                    "kotlin.jvm.functions.Function1:kotlin.jvm.functions.Function3",
        ))
    }

    @Test
    fun `binding and condition annotations stay attached to their IR targets`() {
        val result = compileAndProbe("""
            @file:OptIn(com.yandex.yatagan.ConditionsApi::class)

            package test

            import com.yandex.yatagan.*
            import kotlin.reflect.KClass

            annotation class FeatureA
            annotation class FeatureB

            @Conditionals(
                Conditional(FeatureA::class),
                Conditional(FeatureB::class),
            )
            class ConditionalSubject

            @IntoMap.Key
            annotation class ClassKey(val value: KClass<*>)

            annotation class NestedValue(val value: String)
            annotation class ComplexValue(
                val nested: NestedValue,
                val values: IntArray,
            )

            @Module
            interface MapModule {
                @[Binds IntoMap ClassKey(String::class)]
                fun bind(value: String): CharSequence

                @[Binds IntoMap ClassKey(Any::class) ComplexValue(NestedValue("same"), [1, 2])]
                fun duplicateOne(value: String): CharSequence

                @[Binds IntoMap ClassKey(Any::class) ComplexValue(NestedValue("same"), [1, 2])]
                fun duplicateTwo(value: StringBuilder): CharSequence
            }

            interface Creator {
                @BindsInstance
                fun setValues(values: MutableList<Number>): Creator

                fun create(@BindsInstance name: String): Root
            }

            object Flags {
                val enabled: Boolean = true
            }

            class ConcreteDependency

            @Component(modules = [MapModule::class])
            interface Root
        """.trimIndent())

        assertThat(result).containsAllEntriesOf(mapOf(
            "annotations.conditionals" to "[test.FeatureA, test.FeatureB]",
            "annotations.methodBindsInstance" to "true",
            "annotations.parameterBindsInstance" to "true",
            "annotations.mapMethod" to "true:true",
            "annotations.mapKeyDeclaration" to "true",
            "annotations.mapKeyValue" to "type:java.lang.String",
            "annotations.mapKeyValuesEqual" to "true",
            "annotations.mapKeyValueHashesEqual" to "true",
            "annotations.nestedValuesEqual" to "true",
            "annotations.arrayValuesEqual" to "true",
            "annotations.flagsGetterStatic" to "false",
            "annotations.concreteAbstract" to "false",
        ))
    }

    private fun compileAndProbe(source: String): Map<String, String> {
        val sourceFile = temporaryFolder.newFile("TestCase.kt").apply { writeText(source) }
        val outputDirectory = temporaryFolder.newFolder("classes")
        val probeFile = temporaryFolder.newFile("probe.txt")
        val compilerOutput = ByteArrayOutputStream()
        System.setProperty(ProbeFileProperty, probeFile.absolutePath)
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
                    sourceFile.absolutePath,
                )
            }
        } finally {
            System.clearProperty(ProbeFileProperty)
        }
        assertThat(exitCode)
            .withFailMessage("Compilation failed:\n%s", compilerOutput.toString(Charsets.UTF_8.name()))
            .isEqualTo(ExitCode.OK)
        return probeFile.readLines().associate { line ->
            line.substringBefore('=') to line.substringAfter('=')
        }
    }

    private companion object {
        val testPluginJar: File
            get() = File(checkNotNull(System.getProperty("com.yandex.yatagan.lang.kcp.testPluginJar")))

        val testClasspath: String
            get() = listOf(Component::class.java, Inject::class.java, Unit::class.java)
                .map { type -> File(type.protectionDomain.codeSource.location.toURI()).absolutePath }
                .distinct()
                .joinToString(File.pathSeparator)
    }
}

@OptIn(ExperimentalCompilerApi::class, Internal::class)
class KcpLangProbeRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "com.yandex.yatagan.lang.kcp.test"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(KcpLangProbeExtension())
    }
}

@OptIn(Internal::class)
private class KcpLangProbeExtension : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        if (System.getProperty(ProbeFileProperty) == null) return
        val root = moduleFragment.files.asSequence()
            .flatMap { it.declarations.asSequence() }
            .filterIsInstance<IrClass>()
            .single { it.name.asString() == "Root" }
        val scope = KcpLexicalScope(pluginContext, root.file)
        val declaration = scope.getTypeDeclaration(root)
        val component = checkNotNull(declaration.getAnnotation(BuiltinAnnotation.Component))
        val factoryLookup = scope.ext.langFactory.getTypeDeclaration("test", "Root")
        val reference = scope.getType(pluginContext.irBuiltIns.stringType)
        val nullableReference = scope.getType(pluginContext.irBuiltIns.stringType.makeNullable())
        val primitive = scope.getType(pluginContext.irBuiltIns.intType)
        val boxed = scope.getType(pluginContext.irBuiltIns.intType.makeNullable())
        val generic = scope.getType(
            pluginContext.irBuiltIns.listClass.typeWith(pluginContext.irBuiltIns.stringType),
        )
        val genericWithNullableArgument = scope.getType(
            pluginContext.irBuiltIns.listClass.typeWith(pluginContext.irBuiltIns.stringType.makeNullable()),
        )
        val classesByName = moduleFragment.files.asSequence()
            .flatMap { it.declarations.asSequence() }
            .filterIsInstance<IrClass>()
            .associateBy { it.name.asString() }

        File(checkNotNull(System.getProperty(ProbeFileProperty))).writeText(buildString {
            appendLine("qualifiedName=${declaration.qualifiedName}")
            appendLine("kind=${declaration.kind}")
            appendLine("isAbstract=${declaration.isAbstract}")
            appendLine("isEffectivelyPublic=${declaration.isEffectivelyPublic}")
            appendLine("type=${declaration.asType()}")
            appendLine("annotationClass=${declaration.annotations.first {
                it.annotationClass.qualifiedName == "com.yandex.yatagan.Component"
            }.annotationClass.qualifiedName}")
            appendLine("isRoot=${component.isRoot}")
            appendLine("modules=${component.modules}")
            appendLine("dependencies=${component.dependencies}")
            appendLine("variant=${component.variant}")
            appendLine("multiThreadAccess=${component.multiThreadAccess}")
            appendLine("factoryLookupIsCached=${factoryLookup === declaration}")
            appendLine("platformModel=${(declaration.platformModel as IrClassSymbol).owner.name}")
            appendLine("referenceNullabilityIsErased=${reference === nullableReference}")
            appendLine("primitiveBoxingIsDistinct=${primitive !== boxed}")
            appendLine("primitiveType=$primitive")
            appendLine("boxedType=$boxed")
            appendLine("asBoxedIsCached=${primitive.asBoxed() === boxed}")
            appendLine("genericNullabilityIsErased=${generic === genericWithNullableArgument}")
            appendLine("genericType=$generic")
            declaration.annotations
                .firstOrNull { it.annotationClass.qualifiedName == "test.Config" }
                ?.let { config ->
                    for (attribute in config.annotationClass.attributes) {
                        appendLine("config.${attribute.name}=${config.getValue(attribute).accept(RenderValue)}")
                    }
                }
            classesByName["Subject"]?.let { subject ->
                val subjectDeclaration = scope.getTypeDeclaration(subject)
                val constructors = subjectDeclaration.constructors.toList()
                appendLine("subject.constructor.count=${constructors.size}")
                constructors.singleOrNull()?.let { constructor ->
                    val parameter = constructor.parameters.single()
                    appendLine("subject.constructor.inject=${constructor.getAnnotation(BuiltinAnnotation.Inject) != null}")
                    appendLine("subject.constructor.public=${constructor.isEffectivelyPublic}")
                    appendLine("subject.constructor.parameter=${parameter.name}:${parameter.type}")
                    appendLine("subject.constructor.parameterAnnotations=${parameter.annotations
                        .map { it.annotationClass.qualifiedName }.sorted().toList()}")
                    appendLine("subject.constructor.platformModel=${constructor.platformModel is IrConstructorSymbol}")
                }
            }
            classesByName["TestModule"]?.let { module ->
                val moduleDeclaration = scope.getTypeDeclaration(module)
                val methods = moduleDeclaration.methods.toList()
                val methodsByName = methods.associateBy { it.name }
                appendLine("module.kind=${moduleDeclaration.kind}")
                appendLine("module.methods=${methods.map { it.name }.sorted()}")
                methodsByName["provide"]?.let { method ->
                    val parameter = method.parameters.single()
                    appendLine("module.provide.owner=${method.owner.qualifiedName}")
                    appendLine("module.provide.provides=${method.getAnnotation(BuiltinAnnotation.Provides) != null}")
                    appendLine("module.provide.parameter=${parameter.name}:${parameter.type}")
                    appendLine("module.provide.parameterAnnotations=${parameter.annotations
                        .map { it.annotationClass.qualifiedName }.sorted().toList()}")
                    appendLine("module.provide.static=${method.isStatic}")
                    appendLine("module.provide.platformModel=${method.platformModel is IrSimpleFunctionSymbol}")
                }
                methodsByName["getCount"]?.let { method ->
                    appendLine("module.getCount.annotations=${method.annotations
                        .map { it.annotationClass.qualifiedName }.sorted().toList()}")
                    appendLine("module.getCount.returnType=${method.returnType}")
                    appendLine("module.getCount.provides=${method.getAnnotation(BuiltinAnnotation.Provides) != null}")
                    appendLine("module.getCount.static=${method.isStatic}")
                    appendLine("module.getCount.platformModel=${method.platformModel is IrSimpleFunctionSymbol}")
                }
                methodsByName["inherited"]?.let { method ->
                    appendLine("module.inherited.owner=${method.owner.qualifiedName}")
                    appendLine("module.inherited.provides=${method.getAnnotation(BuiltinAnnotation.Provides) != null}")
                    appendLine("module.inherited.returnType=${method.returnType}")
                }
                methodsByName["staticText"]?.let { method ->
                    appendLine("module.staticText.static=${method.isStatic}")
                }
                methodsByName["visibleProtected"]?.let { method ->
                    appendLine("module.visibleProtected.public=${method.isEffectivelyPublic}")
                }
                val fields = moduleDeclaration.fields.toList()
                appendLine("module.fields=${fields.map { it.name }.sorted()}")
                fields.forEach { field ->
                    appendLine("module.field.${field.name}=${field.type}:${field.isStatic}:" +
                            "${field.platformModel is IrFieldSymbol}")
                }
            }
            val rootMethods = declaration.methods.toList()
            if (classesByName.containsKey("Subject")) {
                appendLine("root.methods=${rootMethods.map { it.name }.sorted()}")
                rootMethods.singleOrNull { it.name == "dependency" }?.let { method ->
                    appendLine("root.dependency.abstract=${method.isAbstract}")
                    appendLine("root.dependency.owner=${method.owner.qualifiedName}")
                    appendLine("root.dependency.platformModel=${method.platformModel is IrSimpleFunctionSymbol}")
                }
            }
            classesByName["Shapes"]?.let { shapes ->
                val methods = scope.getTypeDeclaration(shapes).methods.associateBy { it.name }
                val readOnly = checkNotNull(methods["readOnly"]).returnType
                val mutable = checkNotNull(methods["mutable"]).returnType
                val kotlinClass = checkNotNull(methods["kotlinClass"]).returnType
                val javaClass = checkNotNull(methods["javaClass"]).returnType
                appendLine("shapes.listTypes=$readOnly:$mutable")
                appendLine("shapes.listIdentity=${readOnly === mutable}")
                appendLine("shapes.classTypes=$kotlinClass:$javaClass")
                appendLine("shapes.classIdentity=${kotlinClass === javaClass}")
                appendLine("shapes.functionDeclarations=${methods.getValue("function0").returnType.declaration.qualifiedName}:" +
                        "${methods.getValue("function1").returnType.declaration.qualifiedName}:" +
                        methods.getValue("function3").returnType.declaration.qualifiedName)
            }
            classesByName["ConditionalSubject"]?.let { subject ->
                val conditionals = scope.getTypeDeclaration(subject)
                    .getAnnotations(BuiltinAnnotation.Conditional)
                appendLine("annotations.conditionals=${conditionals.map { it.featureTypes.single().toString() }}")
            }
            classesByName["Creator"]?.let { creator ->
                val methods = scope.getTypeDeclaration(creator).methods.associateBy { it.name }
                appendLine("annotations.methodBindsInstance=${
                    checkNotNull(methods["setValues"]).getAnnotation(BuiltinAnnotation.BindsInstance) != null
                }")
                appendLine("annotations.parameterBindsInstance=${
                    checkNotNull(methods["create"]).parameters.single()
                        .getAnnotation(BuiltinAnnotation.BindsInstance) != null
                }")
            }
            classesByName["MapModule"]?.let { module ->
                val methods = scope.getTypeDeclaration(module).methods.associateBy { it.name }
                val method = checkNotNull(methods["bind"])
                val mapKey = method.annotations.single { it.annotationClass.qualifiedName == "test.ClassKey" }
                appendLine("annotations.mapMethod=${
                    method.getAnnotation(BuiltinAnnotation.IntoMap) != null
                }:${method.getAnnotation(BuiltinAnnotation.Binds) != null}")
                appendLine("annotations.mapKeyDeclaration=${
                    mapKey.annotationClass.getAnnotation(BuiltinAnnotation.IntoMap.Key) != null
                }")
                val valueAttribute = mapKey.annotationClass.attributes.single { it.name == "value" }
                appendLine("annotations.mapKeyValue=${mapKey.getValue(valueAttribute).accept(RenderValue)}")
                val duplicateAnnotations = listOf("duplicateOne", "duplicateTwo").map { name ->
                    checkNotNull(methods[name]).annotations.associateBy { it.annotationClass.qualifiedName }
                }
                val duplicateMapKeys = duplicateAnnotations.map { annotations ->
                    checkNotNull(annotations["test.ClassKey"])
                }
                val duplicateMapKeyValues = duplicateMapKeys.map { duplicateMapKey ->
                    duplicateMapKey.getValue(
                        duplicateMapKey.annotationClass.attributes.single { it.name == "value" },
                    )
                }
                appendLine("annotations.mapKeyValuesEqual=${
                    duplicateMapKeyValues[0] == duplicateMapKeyValues[1]
                }")
                appendLine("annotations.mapKeyValueHashesEqual=${
                    duplicateMapKeyValues[0].hashCode() == duplicateMapKeyValues[1].hashCode()
                }")
                val complexValues = duplicateAnnotations.map { annotations ->
                    val complex = checkNotNull(annotations["test.ComplexValue"])
                    complex.annotationClass.attributes.associate { attribute ->
                        attribute.name to complex.getValue(attribute)
                    }
                }
                appendLine("annotations.nestedValuesEqual=${
                    complexValues[0].getValue("nested") == complexValues[1].getValue("nested")
                }")
                appendLine("annotations.arrayValuesEqual=${
                    complexValues[0].getValue("values") == complexValues[1].getValue("values")
                }")
            }
            classesByName["Flags"]?.let { flags ->
                val getter = scope.getTypeDeclaration(flags).methods.single { it.name == "getEnabled" }
                appendLine("annotations.flagsGetterStatic=${getter.isStatic}")
            }
            classesByName["ConcreteDependency"]?.let { dependency ->
                appendLine("annotations.concreteAbstract=${scope.getTypeDeclaration(dependency).isAbstract}")
            }
        })
    }
}

private object RenderValue : Annotation.Value.Visitor<String> {
    override fun visitDefault(value: Any?): String = error("Unexpected annotation value: $value")
    override fun visitBoolean(value: Boolean) = "boolean:$value"
    override fun visitInt(value: Int) = "int:$value"
    override fun visitString(value: String) = "string:$value"
    override fun visitType(value: Type) = "type:$value"
    override fun visitAnnotation(value: Annotation) = "annotation:$value"
    override fun visitEnumConstant(enum: Type, constant: String) = "enum:$enum.$constant"
    override fun visitArray(value: List<Annotation.Value>) =
        value.joinToString(prefix = "array:[", postfix = "]") { it.accept(this) }

    override fun visitUnresolved() = "unresolved"
}

private const val ProbeFileProperty = "com.yandex.yatagan.lang.kcp.probeFile"
