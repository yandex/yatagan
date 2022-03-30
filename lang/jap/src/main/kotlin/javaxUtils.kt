@file:Suppress("UnstableApiUsage")

package com.yandex.daggerlite.jap.lang

import com.google.auto.common.AnnotationMirrors
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import com.google.common.base.Equivalence
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
import javax.lang.model.element.AnnotationValueVisitor
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.ErrorType
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVisitor
import javax.lang.model.type.WildcardType
import javax.lang.model.util.SimpleAnnotationValueVisitor8

internal inline fun <reified T : Annotation> Element.isAnnotatedWith() =
    MoreElements.getAnnotationMirror(this, T::class.java).isPresent

internal fun TypeMirror.asTypeElement(): TypeElement = MoreTypes.asTypeElement(this)

internal fun TypeMirror.asPrimitiveType(): PrimitiveType = MoreTypes.asPrimitiveType(this)

internal fun TypeMirror.asExecutableType(): ExecutableType = MoreTypes.asExecutable(this)

internal fun TypeMirror.asDeclaredType(): DeclaredType = MoreTypes.asDeclared(this)

internal fun TypeMirror.asWildCardType(): WildcardType = MoreTypes.asWildcard(this)

internal fun TypeMirror.asArrayType(): ArrayType = MoreTypes.asArray(this)

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

private abstract class ExtractingVisitor<T : Any> : SimpleAnnotationValueVisitor8<T, Unit>() {
    final override fun defaultAction(unexpected: Any?, void: Unit) =
        throw AssertionError("Not reached: unexpected annotation value: $unexpected")
}

private object AsBoolean : ExtractingVisitor<Boolean>() {
    override fun visitBoolean(bool: Boolean, void: Unit) = bool
}

private object AsType : ExtractingVisitor<TypeMirror>() {
    override fun visitType(typeMirror: TypeMirror, void: Unit) = typeMirror
    override fun visitString(maybeError: String?, void: Unit) = when (maybeError) {
        ERROR_TYPE_STRING -> ErrorTypeImpl
        else -> throw AssertionError("Not reached: expected type, got: $maybeError")
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

internal val Element.isPrivate
    get() = Modifier.PRIVATE in modifiers

internal val Element.isStatic
    get() = Modifier.STATIC in modifiers

@Suppress("UNCHECKED_CAST")
internal fun TypeElement.allNonPrivateMethods(): Sequence<ExecutableElement> =
    sequenceOf(
        MoreElements.getLocalAndInheritedMethods(this, Utils.types, Utils.elements)
            .asSequence(),
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
            val raw = ClassNameModel(declared.asTypeElement())
            val typeArgs = declared.typeArguments.map(::CtTypeNameModel)
            if (typeArgs.isNotEmpty()) {
                ParameterizedNameModel(raw, typeArgs)
            } else raw
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

        TypeKind.ERROR -> ErrorNameModel("unresolved")
        TypeKind.TYPEVAR -> ErrorNameModel("unsubstituted-type-var")

        else -> throw AssertionError("Not reached: unexpected type: $type")
    }
}

internal fun ClassNameModel(type: TypeElement): ClassNameModel {
    val packageName = type.getPackageElement().qualifiedName.toString()
    val simpleNames = type.qualifiedName.substring(packageName.length + 1).split('.')
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
}

internal object ErrorTypeImpl : ErrorType {
    override fun getAnnotationMirrors(): List<AnnotationMirror> = emptyList()
    override fun getKind(): TypeKind = TypeKind.ERROR
    override fun <A : Annotation?> getAnnotation(clazz: Class<A>) = throw UnsupportedOperationException()
    override fun <A : Annotation?> getAnnotationsByType(clazz: Class<A>) = throw UnsupportedOperationException()
    override fun asElement() = throw UnsupportedOperationException()
    override fun getEnclosingType(): TypeMirror = Utils.types.getNoType(TypeKind.NONE)
    override fun getTypeArguments(): List<TypeMirror> = emptyList()

    override fun <R : Any?, P : Any?> accept(visitor: TypeVisitor<R, P>, param: P): R {
        return visitor.visitError(this, param)
    }
}

internal typealias TypeMirrorEquivalence = Equivalence.Wrapper<TypeMirror>

internal typealias AnnotationMirrorEquivalence = Equivalence.Wrapper<AnnotationMirror>

internal fun TypeMirrorEquivalence(type: TypeMirror): TypeMirrorEquivalence =
    MoreTypes.equivalence().wrap(type)

internal fun AnnotationMirrorEquivalence(annotation: AnnotationMirror): AnnotationMirrorEquivalence =
    AnnotationMirrors.equivalence().wrap(annotation)