/*
 * Copyright 2022 Yandex LLC
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

@file:Suppress("UnstableApiUsage")

package com.yandex.yatagan.lang.jap

import com.google.auto.common.AnnotationMirrors
import com.google.auto.common.AnnotationValues
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import com.google.common.base.Equivalence
import com.yandex.yatagan.base.memoize
import com.yandex.yatagan.lang.Parameter
import com.yandex.yatagan.lang.compiled.ArrayNameModel
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.CtTypeNameModel
import com.yandex.yatagan.lang.compiled.ErrorNameModel
import com.yandex.yatagan.lang.compiled.KeywordTypeNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel
import com.yandex.yatagan.lang.compiled.WildcardNameModel
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.TypeParameterElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.WildcardType
import javax.lang.model.util.AbstractAnnotationValueVisitor8
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.SimpleElementVisitor8

inline fun <reified T : Annotation> Element.isAnnotatedWith() = annotationMirrors.any {
    it.annotationType.asTypeElement().qualifiedName.contentEquals(T::class.java.canonicalName)
}

@PublishedApi
internal fun TypeMirror.asTypeElement(): TypeElement = MoreTypes.asTypeElement(this)

internal fun TypeMirror.asPrimitiveType(): PrimitiveType = MoreTypes.asPrimitiveType(this)

internal fun TypeMirror.asExecutableType(): ExecutableType = MoreTypes.asExecutable(this)

internal fun TypeMirror.asDeclaredType(): DeclaredType = MoreTypes.asDeclared(this)

internal fun TypeMirror.asWildCardType(): WildcardType = MoreTypes.asWildcard(this)

internal fun TypeMirror.asArrayType(): ArrayType = MoreTypes.asArray(this)

fun Element.asTypeElement(): TypeElement = MoreElements.asType(this)

fun Element.asTypeElementOrNull(): TypeElement? = this.accept(AsTypeElementOptional, Unit)

fun Element.asVariableElement(): VariableElement = MoreElements.asVariable(this)

fun Element.asTypeParameterElement(): TypeParameterElement = MoreElements.asTypeParameter(this)

internal fun Element.asExecutableElement() = MoreElements.asExecutable(this)

internal fun Element.getPackageElement(): PackageElement = MoreElements.getPackage(this)

internal val Element.isAbstract
    get() = Modifier.ABSTRACT in modifiers

internal val Element.isPublic
    get() = Modifier.PUBLIC in modifiers

internal val Element.isPrivate
    get() = Modifier.PRIVATE in modifiers

internal val Element.isStatic
    get() = Modifier.STATIC in modifiers

internal val Element.isType
    get() = MoreElements.isType(this)

private val StaticFinal = setOf(Modifier.STATIC, Modifier.FINAL)

private object AsTypeElementOptional : SimpleElementVisitor8<TypeElement?, Unit>() {
    override fun defaultAction(e: Element?, p: Unit?) = null
    override fun visitType(e: TypeElement?, p: Unit?) = e
}

internal fun TypeElement.isDefaultCompanionObject(): Boolean {
    if (!isFromKotlin())
        return false

    if (!simpleName.contentEquals("Companion"))
        return false

    val parent = enclosingElement
    if (!parent.isType)
        return false

    val thisType = asType()
    val equivalence = MoreTypes.equivalence()
    return parent.enclosedElements.any { maybeField ->
        maybeField.kind == ElementKind.FIELD && maybeField.simpleName.contentEquals("Companion") &&
                maybeField.modifiers.containsAll(StaticFinal) &&
                equivalence.equivalent(maybeField.asType(), thisType)
    }
}

internal fun TypeElement.isKotlinSingleton(): Boolean {
    if (!isFromKotlin())
        return false

    val thisType = asType()
    val equivalence = MoreTypes.equivalence()
    return enclosedElements.any { maybeField ->
        maybeField.kind == ElementKind.FIELD && maybeField.simpleName.contentEquals("INSTANCE") &&
                maybeField.modifiers.containsAll(StaticFinal) &&
                equivalence.equivalent(maybeField.asType(), thisType)
    }
}

internal fun TypeElement.isFromKotlin(): Boolean {
    return isAnnotatedWith<Metadata>()
}

internal tailrec fun Element.isFromKotlin(): Boolean {
    // For a random element need to find a type element it belongs to first.
    return asTypeElementOrNull()?.isFromKotlin() ?: (enclosingElement ?: return false).isFromKotlin()
}

internal fun TypeElement.allNonPrivateFields(): Sequence<VariableElement> = sequence {
    suspend fun SequenceScope<VariableElement>.addFieldsFrom(declaration: TypeElement) {
        if (declaration == Utils.objectType) {
            return
        }
        for (field in ElementFilter.fieldsIn(declaration.enclosedElements)) {
            if (!field.isPrivate) {
                yield(field)
            }
        }
        val superclass = declaration.superclass
        if (superclass.kind != TypeKind.NONE) {
            addFieldsFrom(superclass.asTypeElement())
        }
    }
    addFieldsFrom(this@allNonPrivateFields)
}

internal fun TypeElement.allNonPrivateMethods(): Sequence<ExecutableElement> =
    sequenceOf(
        MoreElements.getLocalAndInheritedMethods(this, Utils.types, Utils.elements)
            .asSequence()
            .filter {
                // Skip methods from Object.
                it.enclosingElement != Utils.objectType
            }.distinctBy {
                TypeMirrorEquivalence(it.asType()) to it.simpleName
            },
        enclosedElements
            .asSequence()
            .filter {
                it.kind == ElementKind.METHOD && it.isStatic && !it.isPrivate
            }.map(Element::asExecutableElement),
    ).flatten()

internal fun TypeElement.allImplementedInterfaces(): Sequence<TypeMirror> = sequence {
    val queue = ArrayDeque<TypeMirror>()
    queue += interfaces
    if (superclass.kind != TypeKind.NONE) {
        queue += superclass
    }
    while (queue.isNotEmpty()) {
        val type = queue.removeFirst()
        val element = type.asTypeElement()
        queue += element.interfaces
        val superClassElement = element.superclass
        if (superClassElement.kind != TypeKind.NONE) {
            queue += superClassElement
        }
        if (element.kind == ElementKind.INTERFACE) {
            yield(type)
        }
    }
}

internal fun CtTypeNameModel(type: TypeMirror): CtTypeNameModel {
    return when (type.kind) {
        TypeKind.DECLARED -> {
            val declared = type.asDeclaredType()
            val declaration = declared.asTypeElement()
            if (declaration.qualifiedName.contentEquals("error.NonExistentClass")) {
                // This is KAPT's stub error type - it's not actual error type,
                //  it's a real class that's generated by KAPT.
                ErrorNameModel()
            } else {
                val raw = ClassNameModel(declaration)
                val typeArgs = declared.typeArguments.map(::CtTypeNameModel)
                if (typeArgs.isNotEmpty()) {
                    ParameterizedNameModel(raw, typeArgs)
                } else raw
            }
        }
        TypeKind.WILDCARD -> {
            val wildcard = type.asWildCardType()
            WildcardNameModel(
                lowerBound = wildcard.superBound?.let(::CtTypeNameModel),
                upperBound = wildcard.extendsBound?.let(::CtTypeNameModel),
            )
        }
        TypeKind.VOID -> KeywordTypeNameModel.Void
        TypeKind.BOOLEAN -> KeywordTypeNameModel.Boolean
        TypeKind.BYTE -> KeywordTypeNameModel.Byte
        TypeKind.SHORT -> KeywordTypeNameModel.Short
        TypeKind.INT -> KeywordTypeNameModel.Int
        TypeKind.LONG -> KeywordTypeNameModel.Long
        TypeKind.CHAR -> KeywordTypeNameModel.Char
        TypeKind.FLOAT -> KeywordTypeNameModel.Float
        TypeKind.DOUBLE -> KeywordTypeNameModel.Double

        TypeKind.ARRAY -> ArrayNameModel(CtTypeNameModel(type.asArrayType().componentType))

        TypeKind.ERROR, TypeKind.NULL, TypeKind.TYPEVAR -> ErrorNameModel()

        else -> throw AssertionError("Not reached: unexpected type: $type")
    }
}

internal fun ClassNameModel(type: TypeElement): ClassNameModel {
    val packageName = type.getPackageElement().qualifiedName.toString()
    val simpleNames = type.qualifiedName.run {
        if (packageName.isNotEmpty()) substring(packageName.length + 1) else this
    }.split('.')
    return ClassNameModel(packageName, simpleNames)
}

internal fun Element.asMemberOf(type: DeclaredType): TypeMirror {
    return Utils.types.asMemberOf(type, this)
}

internal fun parametersSequenceFor(
    element: ExecutableElement,
    asMemberOf: DeclaredType,
) = sequence<Parameter> {
    val parameters = element.parameters
    val types = element.asMemberOf(asMemberOf).asExecutableType().parameterTypes
    for (i in parameters.indices) {
        yield(JavaxParameterImpl(impl = parameters[i], refinedType = types[i]))
    }
}.memoize()

internal typealias TypeMirrorEquivalence = Equivalence.Wrapper<TypeMirror>

internal typealias AnnotationMirrorEquivalence = Equivalence.Wrapper<AnnotationMirror>

internal typealias AnnotationValueEquivalence = Equivalence.Wrapper<AnnotationValue>

internal fun TypeMirrorEquivalence(type: TypeMirror): TypeMirrorEquivalence =
    MoreTypes.equivalence().wrap(type)

internal fun AnnotationMirrorEquivalence(annotation: AnnotationMirror): AnnotationMirrorEquivalence =
    AnnotationMirrors.equivalence().wrap(annotation)

internal fun AnnotationValueEquivalence(value: AnnotationValue): AnnotationValueEquivalence =
    AnnotationValues.equivalence().wrap(value)

internal abstract class AbstractAnnotationValueVisitor8Adapter<T> : AbstractAnnotationValueVisitor8<T, Nothing?>() {
    override fun visitBoolean(b: Boolean, p: Nothing?): T = visitDefault()
    override fun visitByte(b: Byte, p: Nothing?): T = visitDefault()
    override fun visitChar(c: Char, p: Nothing?): T = visitDefault()
    override fun visitDouble(d: Double, p: Nothing?): T = visitDefault()
    override fun visitFloat(f: Float, p: Nothing?): T = visitDefault()
    override fun visitInt(i: Int, p: Nothing?): T = visitDefault()
    override fun visitLong(i: Long, p: Nothing?): T = visitDefault()
    override fun visitShort(s: Short, p: Nothing?): T = visitDefault()
    override fun visitString(s: String?, p: Nothing?): T = visitDefault()
    override fun visitType(t: TypeMirror, p: Nothing?): T = visitDefault()
    override fun visitEnumConstant(c: VariableElement, p: Nothing?): T = visitDefault()
    override fun visitAnnotation(a: AnnotationMirror, p: Nothing?): T = visitDefault()
    override fun visitArray(vals: MutableList<out AnnotationValue>, p: Nothing?): T = visitDefault()
    abstract fun visitDefault(): T
}