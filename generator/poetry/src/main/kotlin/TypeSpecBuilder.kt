package com.yandex.dagger3.generator.poetry

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import kotlin.reflect.KClass

@JavaPoetry
abstract class TypeSpecBuilder : AnnotatibleBuilder {
    @PublishedApi
    internal abstract val impl: TypeSpec.Builder

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

    fun generic(vararg typeVars: TypeVariableName) {
        typeVars.forEach(impl::addTypeVariable)
    }
}