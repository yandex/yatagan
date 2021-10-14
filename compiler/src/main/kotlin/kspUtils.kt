package com.yandex.dagger3.compiler

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSType
import kotlin.reflect.KClass


internal infix fun <A : Annotation> KSAnnotation.hasType(clazz: KClass<A>): Boolean {
    return shortName.getShortName() == clazz.simpleName &&
            annotationType.resolve().declaration.qualifiedName?.asString() ==
            clazz.qualifiedName
}

internal inline fun <reified A : Annotation> KSAnnotated.getAnnotation(): KSAnnotation? {
    return annotations.find { it hasType A::class }
}

internal inline fun <reified A : Annotation> KSType.getAnnotation(): KSAnnotation? {
    return annotations.find { it hasType A::class }
}

internal operator fun KSAnnotation.get(name: String): Any? {
    return arguments.find { (it.name?.asString() ?: "value") == name }?.value
}

internal inline fun <reified T : Annotation> KSAnnotated.isAnnotationPresent(): Boolean =
    annotations.any { it hasType T::class }