@file:Suppress("UnstableApiUsage")

package com.yandex.daggerlite.jap.lang

import com.google.auto.common.AnnotationMirrors
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import com.yandex.daggerlite.generator.lang.ClassNameModel
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.AnnotationValueVisitor
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.SimpleAnnotationValueVisitor8
import javax.lang.model.util.Types

internal inline fun <reified T : Annotation> Element.isAnnotatedWith() =
    MoreElements.getAnnotationMirror(this, T::class.java).isPresent

internal fun TypeMirror.asTypeElement(): TypeElement = MoreTypes.asTypeElement(this)

internal fun TypeMirror.asPrimitiveType() = MoreTypes.asPrimitiveType(this)

internal fun TypeMirror.asDeclaredType() = MoreTypes.asDeclared(this)

internal fun AnnotationMirror.typesValue(param: String): Sequence<TypeMirror> =
    AnnotationMirrors.getAnnotationValue(this, param).accept(AsTypes)

internal fun AnnotationMirror.typeValue(param: String): TypeMirror =
    AnnotationMirrors.getAnnotationValue(this, param).accept(AsType)

internal fun AnnotationMirror.booleanValue(param: String): Boolean =
    AnnotationMirrors.getAnnotationValue(this, param).accept(AsBoolean)

internal fun AnnotationMirror.stringValue(param: String): String {
    return AnnotationMirrors.getAnnotationValue(this, param).accept(AsString)
}

internal fun AnnotationMirror.annotationsValue(param: String): Sequence<AnnotationMirror> {
    return AnnotationMirrors.getAnnotationValue(this, param).accept(AsAnnotations)
}

internal fun AnnotationMirror.annotationValue(param: String): AnnotationMirror {
    return AnnotationMirrors.getAnnotationValue(this, param).accept(AsAnnotation)
}

internal fun <R> AnnotationValue.accept(visitor: AnnotationValueVisitor<R, Unit>): R = accept(visitor, Unit)

/**
 * This is "javac magic". Sometimes it reports "Attribute$UnresolvedClass" as string "<error>".
 * Nothing we can do about it.
 */
private const val ERROR_TYPE_STRING = "<error>"

// TODO: handle it somewhere to defer element processing
internal class ErrorTypeException : RuntimeException()

private abstract class ExtractingVisitor<T : Any> : SimpleAnnotationValueVisitor8<T, Unit>() {
    final override fun defaultAction(unexpected: Any?, void: Unit) =
        throw AssertionError("Unexpected annotation value: $unexpected")
}

private object AsBoolean : ExtractingVisitor<Boolean>() {
    override fun visitBoolean(bool: Boolean, void: Unit) = bool
}

private object AsType : ExtractingVisitor<TypeMirror>() {
    override fun visitType(typeMirror: TypeMirror, void: Unit) = typeMirror
    override fun visitString(maybeError: String?, void: Unit) = throw when (maybeError) {
        ERROR_TYPE_STRING -> ErrorTypeException()
        else -> AssertionError(maybeError)
    }
}

private object AsTypes : ExtractingVisitor<Sequence<TypeMirror>>() {
    override fun visitArray(values: List<AnnotationValue>, void: Unit) =
        values.asSequence().map { value -> value.accept(AsType, void) }
}

private object AsString : ExtractingVisitor<String>() {
    override fun visitString(str: String, void: Unit) = str
}

private object AsAnnotation : ExtractingVisitor<AnnotationMirror>() {
    override fun visitAnnotation(annotation: AnnotationMirror, void: Unit) = annotation
}

private object AsAnnotations : ExtractingVisitor<Sequence<AnnotationMirror>>() {
    override fun visitArray(values: List<AnnotationValue>, void: Unit) =
        values.asSequence().map { value -> value.accept(AsAnnotation, void) }
}

fun Element.asTypeElement(): TypeElement = MoreElements.asType(this)

fun Element.asVariableElement(): VariableElement = MoreElements.asVariable(this)

internal fun Element.asExecutableElement() = MoreElements.asExecutable(this)

internal fun Element.getPackageElement(): PackageElement = MoreElements.getPackage(this)

internal val Element.isAbstract
    get() = Modifier.ABSTRACT in modifiers

internal val Element.isPublic
    get() = Modifier.PUBLIC in modifiers

internal val Element.isStatic
    get() = Modifier.STATIC in modifiers

internal val TypeElement.isKotlin: Boolean
    get() = annotationMirrors.any { it.annotationType.toString() == "kotlin.Metadata" }

@Suppress("UNCHECKED_CAST")
internal fun TypeElement.allMethods(typeUtils: Types, elementUtils: Elements): Sequence<ExecutableElement> =
    sequence<ExecutableElement> {
        yieldAll(MoreElements.getLocalAndInheritedMethods(this@allMethods, typeUtils, elementUtils))
        yieldAll(enclosedElements.filter {
            it.kind == ElementKind.METHOD && Modifier.STATIC in it.modifiers
        }.map { it.asExecutableElement() })
    }

// TODO: Как и в todo ниже, можно использовать библиотеку для выявления котлин обжекта.
fun TypeElement.getCompanionObject(): TypeElement? =
    ElementFilter.typesIn(enclosedElements).find { it.simpleName.contentEquals("Companion") && it.isKotlin }

// TODO: вероятно тут стоит использовать библиотеку `org.jetbrains.kotlinx:kotlinx-metadata-jvm`,
//  чтобы избежать возможных ошибок с выявлением котлин обжекта.
internal val TypeElement.isKotlinObject
    get() = isKotlin && ElementFilter.fieldsIn(enclosedElements).any { field ->
        field.isPublic && field.isStatic && field.simpleName.contentEquals("INSTANCE")
                && field.asType().asTypeElement() == this
    }

internal fun ClassNameModel(type: TypeMirror): ClassNameModel {
    val typeArgs = (type as? DeclaredType)?.typeArguments?.map {
        ClassNameModel(it)
    } ?: emptyList()
    return when(type.kind) {
        TypeKind.DECLARED -> ClassNameModel(type.asTypeElement()).withArguments(typeArgs)
        TypeKind.WILDCARD -> ClassNameModel("", listOf("?"), emptyList())
        else -> throw RuntimeException("Unexpected type: $type")
    }
}

internal fun ClassNameModel(type: TypeElement): ClassNameModel {
    val packageName = type.getPackageElement().qualifiedName.toString()
    val simpleNames = type.qualifiedName.substring(packageName.length + 1).split('.')

    return ClassNameModel(packageName, simpleNames, emptyList())
}