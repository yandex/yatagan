package com.yandex.daggerlite.compiler

import com.google.auto.common.AnnotationMirrors
import dagger.Component
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.AnnotationValueVisitor
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.SimpleAnnotationValueVisitor8

val TypeElement.isRoot get() = this.getAnnotationMirror<Component>().booleanValue("isRoot")

fun AnnotationMirror.typesValue(param: String = "value"): Sequence<TypeElement> =
    AnnotationMirrors.getAnnotationValue(this, param).accept(AsTypes).map(TypeMirror::asTypeElement)

fun AnnotationMirror.booleanValue(param: String = "value"): Boolean {
    return AnnotationMirrors.getAnnotationValue(this, param).accept(AsBoolean)
}

fun <R> AnnotationValue.accept(visitor: AnnotationValueVisitor<R, Unit>): R {
    return accept(visitor, Unit)
}

/**
 * This is "javac magic". Sometimes it reports "Attribute$UnresolvedClass" as string "<error>".
 * Nothing we can do about it.
 */
private const val ERROR_TYPE_STRING = "<error>"

class ErrorTypeException : RuntimeException()

abstract class ExtractingVisitor<T : Any> : SimpleAnnotationValueVisitor8<T, Unit>() {
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