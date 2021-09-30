// Copyright 2020 Yandex LLC. All rights reserved.

package com.yandex.dagger3.compiler

import com.squareup.javapoet.*
import javax.annotation.processing.Filer
import javax.lang.model.element.*
import kotlin.reflect.KClass

@DslMarker
annotation class JavaPoetry

open class ExpressionBuilder {
    val impl: CodeBlock.Builder = CodeBlock.builder()

    operator fun CodeBlock.unaryPlus() {
        impl.add(this)
    }

    operator fun String.unaryPlus() {
        impl.add(this)
    }

    inline fun <T> join(seq: Sequence<T>,
                        separator: String = ", ",
                        crossinline block: ExpressionBuilder.(T) -> Unit
    ) {
        impl.add(CodeBlock.join(seq.map { buildExpression { block(it) } }.asIterable(), separator))
    }

    fun String.formatCode(vararg args: Any): CodeBlock {
        return CodeBlock.of(this.replace('%', '$'), *args)
    }
}

@JavaPoetry
open class CodeBuilder {
    val implCode: CodeBlock.Builder = CodeBlock.builder()

    operator fun CodeBlock.unaryPlus() {
        implCode.addStatement(this)
    }

    operator fun String.unaryPlus() {
        implCode.addStatement(this)
    }

    fun String.formatCode(vararg args: Any): CodeBlock {
        return CodeBlock.of(this.replace('%', '$'), *args)
    }

    inline fun controlFlow(code: CodeBlock, block: CodeBuilder.() -> Unit) {
        implCode.beginControlFlow("\$L", code)
        block()
        implCode.endControlFlow()
    }

    inline fun controlFlow(code: String, block: CodeBuilder.() -> Unit) {
        implCode.beginControlFlow("\$L", code)
        block()
        implCode.endControlFlow()
    }
}

interface AnnotatibleBuilder {
    fun annotation(mirror: AnnotationMirror)

    fun annotation(clazz: KClass<out Annotation>, block: AnnotationSpecBuilder.() -> Unit)
}

@JavaPoetry
abstract class MethodSpecBuilder : CodeBuilder(), AnnotatibleBuilder {
    abstract val impl: MethodSpec.Builder

    fun implBuild(): MethodSpec {
        impl.addCode(implCode.build())
        return impl.build()
    }

    inline fun parameter(type: TypeName, name: String,
                         block: ParameterSpecBuilder.() -> Unit = {}
    ) {
        impl.addParameter(ParameterSpecBuilder(type, name).apply(block).impl.build())
    }

    inline fun parameter(type: TypeName, name: Name, block: ParameterSpecBuilder.() -> Unit = {}) {
        parameter(type, name.toString(), block)
    }

    inline fun <reified A : Annotation> annotation(block: AnnotationSpecBuilder.() -> Unit = {}) {
        impl.addAnnotation(AnnotationSpecBuilder(A::class).apply(block).impl.build())
    }

    fun annotation(name: ClassName) {
        impl.addAnnotation(name)
    }

    override fun annotation(mirror: AnnotationMirror) {
        impl.addAnnotation(AnnotationSpec.get(mirror))
    }

    override fun annotation(
            clazz: KClass<out Annotation>,
            block: AnnotationSpecBuilder.() -> Unit,
    ) {
        impl.addAnnotation(AnnotationSpecBuilder(clazz).apply(block).impl.build())
    }

    fun returnType(type: TypeName) {
        impl.returns(type)
    }

    fun modifiers(vararg modifiers: Modifier) {
        impl.addModifiers(*modifiers)
    }

    inline fun defaultValue(block: ExpressionBuilder.() -> Unit) {
        impl.defaultValue(buildExpression(block))
    }
}

@JavaPoetry
class FieldSpecBuilder(type: TypeName, name: String) {
    val impl: FieldSpec.Builder = FieldSpec.builder(type, name)

    fun modifiers(vararg modifiers: Modifier) {
        impl.addModifiers(*modifiers)
    }

    inline fun initializer(block: ExpressionBuilder.() -> Unit) {
        impl.initializer(buildExpression(block))
    }

    fun initializer(code: CodeBlock) {
        impl.initializer(code)
    }
}

@JavaPoetry
class ParameterSpecBuilder constructor(type: TypeName, name: String) : AnnotatibleBuilder {
    val impl: ParameterSpec.Builder = ParameterSpec.builder(type, name)

    inline fun <reified A : Annotation> annotation(
            block: AnnotationSpecBuilder.() -> Unit = {}
    ) {
        impl.addAnnotation(AnnotationSpecBuilder(A::class).apply(block).impl.build())
    }

    override fun annotation(mirror: AnnotationMirror) {
        impl.addAnnotation(AnnotationSpec.get(mirror))
    }

    override fun annotation(
            clazz: KClass<out Annotation>,
            block: AnnotationSpecBuilder.() -> Unit,
    ) {
        impl.addAnnotation(AnnotationSpecBuilder(clazz).apply(block).impl.build())
    }
}

@JavaPoetry
abstract class TypeSpecBuilder : AnnotatibleBuilder {
    abstract val impl: TypeSpec.Builder

    inline fun method(name: String, block: MethodSpecBuilder.() -> Unit) {
        impl.addMethod(MethodSpecBuilderImpl(name).apply(block).implBuild())
    }

    inline fun constructor(block: MethodSpecBuilder.() -> Unit) {
        impl.addMethod(ConstructorSpecBuilder().apply(block).implBuild())
    }

    inline fun override(method: ExecutableElement, block: MethodSpecBuilder.() -> Unit) {
        impl.addMethod(OverrideMethodSpecBuilder(method).apply(block).implBuild())
    }

    inline fun field(type: TypeName, name: String, block: FieldSpecBuilder.() -> Unit = {}) {
        impl.addField(FieldSpecBuilder(type, name).apply(block).impl.build())
    }

    inline fun <reified A : Annotation> annotation(block: AnnotationSpecBuilder.() -> Unit = {}) {
        impl.addAnnotation(AnnotationSpecBuilder(A::class).apply(block).impl.build())
    }

    override fun annotation(mirror: AnnotationMirror) {
        impl.addAnnotation(AnnotationSpec.get(mirror))
    }

    override fun annotation(
            clazz: KClass<out Annotation>,
            block: AnnotationSpecBuilder.() -> Unit,
    ) {
        impl.addAnnotation(AnnotationSpecBuilder(clazz).apply(block).impl.build())
    }

    fun annotation(name: ClassName) {
        impl.addAnnotation(name)
    }

    fun implements(typeName: TypeName) {
        impl.addSuperinterface(typeName)
    }

    fun extends(typeName: TypeName) {
        impl.superclass(typeName)
    }

    fun originatingElement(element: Element) {
        impl.addOriginatingElement(element)
    }

    inline fun nestedType(block: () -> TypeSpec) {
        impl.addType(block())
    }

    fun modifiers(vararg modifiers: Modifier) {
        impl.addModifiers(*modifiers)
    }
}

@JavaPoetry
class AnnotationSpecBuilder(
        clazz: KClass<out Annotation>
) {
    val impl: AnnotationSpec.Builder = AnnotationSpec.builder(clazz.java)

    inline fun <reified E : Enum<E>> enumValue(value: E, name: String = "value") {
        impl.addMember(name, "\$T.\$N", E::class.java, value.name)
    }

    fun classValue(type: ClassName, name: String = "value") {
        impl.addMember(name, "\$T.class", type)
    }

    fun classValues(vararg types: ClassName, name: String = "value") {
        value(name) {
            +"{"
            join(types.asSequence()) { type -> +"%T.class".formatCode(type) }
            +"}"
        }
    }

    inline fun value(name: String = "value", block: ExpressionBuilder.() -> Unit) {
        impl.addMember(name, buildExpression(block))
    }

    fun stringValue(value: String, name: String = "value") {
        impl.addMember(name, buildExpression { +"%S".formatCode(value) })
    }

    inline fun <reified A : Annotation> annotation(
            block: AnnotationSpecBuilder.() -> Unit = {}
    ): AnnotationSpec {
        return AnnotationSpecBuilder(A::class).apply(block).impl.build()
    }
}

class ClassTypeSpecBuilder(name: ClassName) : TypeSpecBuilder() {
    override val impl: TypeSpec.Builder = TypeSpec.classBuilder(name)
}

class InterfaceTypeSpecBuilder(name: ClassName) : TypeSpecBuilder() {
    override val impl: TypeSpec.Builder = TypeSpec.interfaceBuilder(name)
}

class AnnotationTypeSpecBuilder(name: ClassName) : TypeSpecBuilder() {
    override val impl: TypeSpec.Builder = TypeSpec.annotationBuilder(name)
}

class MethodSpecBuilderImpl(name: String) : MethodSpecBuilder() {
    override val impl: MethodSpec.Builder = MethodSpec.methodBuilder(name)
}

class ConstructorSpecBuilder : MethodSpecBuilder() {
    override val impl: MethodSpec.Builder = MethodSpec.constructorBuilder()
}

class OverrideMethodSpecBuilder(base: ExecutableElement) : MethodSpecBuilder() {
    override val impl: MethodSpec.Builder = MethodSpec.overriding(base)
}

inline fun buildInterface(
        name: ClassName, block: TypeSpecBuilder.() -> Unit
): TypeSpec = InterfaceTypeSpecBuilder(name).apply(block).impl.build()

inline fun buildAnnotationClass(
        name: ClassName, block: TypeSpecBuilder.() -> Unit
): TypeSpec = AnnotationTypeSpecBuilder(name).apply(block).impl.build()

inline fun buildClass(
        name: ClassName, block: TypeSpecBuilder.() -> Unit
): TypeSpec = ClassTypeSpecBuilder(name).apply(block).impl.build()

inline fun buildExpression(block: ExpressionBuilder.() -> Unit): CodeBlock {
    return ExpressionBuilder().apply(block).impl.build()
}

fun TypeSpec.writeToJavaFile(packageName: String, filer: Filer) {
    JavaFile.builder(packageName, this).build().writeTo(filer)
}
