@file:Suppress("UnstableApiUsage")

package com.yandex.daggerlite.jap

import com.google.auto.common.MoreElements
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

fun Element.isTypeElement() = MoreElements.isType(this)

fun Element.asTypeElement(): TypeElement = MoreElements.asType(this)

fun Element.asMethod(): ExecutableElement = MoreElements.asExecutable(this)

fun Element.getPackageElement(): PackageElement = MoreElements.getPackage(this)

fun TypeElement.methods(): List<ExecutableElement> = ElementFilter.methodsIn(enclosedElements)

fun TypeElement.types() = ElementFilter.typesIn(enclosedElements)

fun TypeElement.constructors(): List<ExecutableElement> = ElementFilter.constructorsIn(enclosedElements)

fun TypeElement.allMethods(typeUtils: Types, elementUtils: Elements): Collection<ExecutableElement> =
    MoreElements.getLocalAndInheritedMethods(this, typeUtils, elementUtils) + this.methods().filter { it.isStatic }

inline fun <reified T : Annotation> Element.getAnnotationMirror(): AnnotationMirror =
    MoreElements.getAnnotationMirror(this, T::class.java).get()

inline fun <reified T : Annotation> Element.getAnnotationMirrorIfPresent(): AnnotationMirror? =
    MoreElements.getAnnotationMirror(this, T::class.java).orNull()

inline fun <reified T : Annotation> Element.isAnnotatedWith() =
    MoreElements.getAnnotationMirror(this, T::class.java).isPresent

val Element.isAbstract get() = Modifier.ABSTRACT in modifiers

val Element.isPublic get() = Modifier.PUBLIC in modifiers

val Element.isStatic get() = Modifier.STATIC in modifiers

val TypeElement.isKotlin: Boolean get() = annotationMirrors.any { it.annotationType.toString() == "kotlin.Metadata" }

// todo: вероятно тут стоит использовать библиотеку `org.jetbrains.kotlinx:kotlinx-metadata-jvm`, чтобы избежать возможных ошибок с выявлением котлин обжекта.
val TypeElement.isKotlinObject
    get() = isKotlin && ElementFilter.fieldsIn(enclosedElements).any { field ->
        field.isPublic && field.isStatic && field.asType().asTypeElement() == this
                && field.simpleName.contentEquals("INSTANCE")
    }
