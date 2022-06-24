@file:Suppress("UnstableApiUsage")

package com.yandex.daggerlite.jap.lang

import com.google.auto.common.AnnotationMirrors
import com.google.auto.common.AnnotationValues
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import com.google.common.base.Equivalence
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.generator.lang.ArrayNameModel
import com.yandex.daggerlite.generator.lang.ClassNameModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import com.yandex.daggerlite.generator.lang.ErrorNameModel
import com.yandex.daggerlite.generator.lang.KeywordTypeNameModel
import com.yandex.daggerlite.generator.lang.ParameterizedNameModel
import com.yandex.daggerlite.generator.lang.WildcardNameModel
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.WildcardType
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
) = sequence<ParameterLangModel> {
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
