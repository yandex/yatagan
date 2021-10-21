package com.yandex.daggerlite.compiler

import com.google.auto.common.MoreTypes
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror

fun TypeMirror.asElement() = MoreTypes.asElement(this)

fun TypeMirror.asTypeElement(): TypeElement = MoreTypes.asTypeElement(this)

fun TypeMirror.asDeclaredType(): DeclaredType = MoreTypes.asDeclared(this)

fun Element.getTypeElement(): TypeElement = MoreTypes.asTypeElement(this.asType())
