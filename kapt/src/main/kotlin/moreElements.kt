@file:Suppress("UnstableApiUsage")

package com.yandex.daggerlite.compiler

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

fun TypeElement.allMethods(typeUtils: Types, elementUtils: Elements): Set<ExecutableElement> =
    MoreElements.getLocalAndInheritedMethods(this, typeUtils, elementUtils)

inline fun <reified T : Annotation> Element.getAnnotationMirror(): AnnotationMirror =
    MoreElements.getAnnotationMirror(this, T::class.java).get()

inline fun <reified T : Annotation> Element.getAnnotationMirrorIfPresent(): AnnotationMirror? =
    MoreElements.getAnnotationMirror(this, T::class.java).orNull()

inline fun <reified T : Annotation> Element.isAnnotatedWith() =
    MoreElements.getAnnotationMirror(this, T::class.java).isPresent

val Element.isAbstract get() = Modifier.ABSTRACT in modifiers
