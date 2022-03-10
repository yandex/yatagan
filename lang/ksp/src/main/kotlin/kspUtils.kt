package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.NonExistLocation
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Origin
import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.ParameterLangModel
import kotlin.reflect.KClass


internal fun <A : Annotation> KSAnnotation.hasType(clazz: KClass<A>): Boolean {
    return shortName.getShortName() == clazz.simpleName &&
            annotationType.resolve().declaration.qualifiedName?.asString() ==
            clazz.qualifiedName
}

internal operator fun KSAnnotation.get(name: String): Any? {
    return arguments.find { (it.name?.asString() ?: "value") == name }?.value
}

internal inline fun <reified T : Annotation> KSAnnotated.isAnnotationPresent(): Boolean =
    annotations.any { it.hasType(T::class) }

internal val KSDeclaration.isStatic get() = Modifier.JAVA_STATIC in modifiers || isAnnotationPresent<JvmStatic>()

internal val KSFunctionDeclaration.isStatic get() = functionKind == FunctionKind.STATIC

internal val KSDeclaration.isObject get() = this is KSClassDeclaration && classKind == ClassKind.OBJECT

internal val KSPropertyDeclaration.isField
    get() = isFromJava || (getter == null && setter == null) ||
            isAnnotationPresent<JvmField>() || Modifier.CONST in modifiers

internal fun KSTypeReference?.resolveOrError(): KSType {
    return this?.resolve() ?: ErrorTypeImpl
}

internal fun KSTypeReference?.orError(): KSTypeReference {
    return this ?: ErrorTypeImpl.reference()
}

internal val KSNode.isFromJava
    get() = when (origin) {
        Origin.JAVA, Origin.JAVA_LIB -> true
        else -> false
    }

/**
 * Attempts to resolve [KSClassDeclaration], resolving type aliases as needed.
 */
internal fun KSType.getNonAliasDeclaration(): KSClassDeclaration? = when (val declaration = declaration) {
    is KSClassDeclaration -> declaration
    is KSTypeAlias -> declaration.type.resolve().getNonAliasDeclaration()
    else -> null
}

private class MappedReference private constructor(
    val original: KSTypeReference,
    val mappedType: KSType,
) : KSTypeReference by original {
    override fun resolve(): KSType = mappedType

    override fun toString() = "mapped-reference{$original -> $mappedType}"

    companion object Factory : BiObjectCache<KSTypeReference, KSType, MappedReference>() {
        operator fun invoke(
            original: KSTypeReference,
            mappedType: KSType,
        ): MappedReference = createCached(original, mappedType) {
            MappedReference(original, mappedType)
        }
    }
}

fun KSTypeReference.replaceType(type: KSType): KSTypeReference {
    return when (this) {
        is MappedReference -> MappedReference(original = original, mappedType = type)
        else -> MappedReference(original = this, mappedType = type)
    }
}

internal fun KSType.reference(): KSTypeReference = Utils.resolver.createKSTypeReferenceFromKSType(this)

internal fun KSClassDeclaration.getCompanionObject(): KSClassDeclaration? =
    declarations.filterIsInstance<KSClassDeclaration>().find(KSClassDeclaration::isCompanionObject)

internal fun KSClassDeclaration.allPublicFunctions(): Sequence<KSFunctionDeclaration> = sequence {
    if (classKind == ClassKind.INTERFACE) {
        for (function in getAllFunctions()) {
            when (function.simpleName.asString()) {
                // This is necessary to drop `equals`, `hashCode`, `toString` from `Any`.
                // KSP implicitly adds them to the interface functions for some reason.
                // TODO: invent something more subtle
                "equals", "hashCode", "toString" -> Unit
                else -> yield(function)
            }
        }
    } else {
        // For non-interface, return everything public, except constructors.
        for (function in getAllFunctions()) {
            if (!function.isConstructor() && !function.isPrivate()) {
                yield(function)
            }
        }
    }
    // Yield all declared static functions
    for (declaredFunction in getDeclaredFunctions()) {
        if (declaredFunction.functionKind == FunctionKind.STATIC) {
            yield(declaredFunction)
        }
    }
}

internal fun KSClassDeclaration.allPublicProperties(): Sequence<KSPropertyDeclaration> {
    return getAllProperties().filter { !it.isPrivate() && !it.isField }
}

internal fun annotationsFrom(impl: KSAnnotated) = impl.annotations.map { KspAnnotationImpl(it) }.memoize()

internal fun parametersSequenceFor(
    declaration: KSFunctionDeclaration,
    jvmMethodSignature: JvmMethodSignature,
    containing: KSType,
) = sequence<ParameterLangModel> {
    val parameters = declaration.parameters
    val types = declaration.asMemberOf(containing).parameterTypes
    for (i in parameters.indices) {
        val parameter = parameters[i]
        yield(KspParameterImpl(
            impl = parameter,
            jvmSignatureSupplier = { jvmMethodSignature.parameterTypesSignatures?.get(i) },
            refinedTypeRef = parameter.type.replaceType(types[i] ?: ErrorTypeImpl),
        ))
    }
}


internal object ErrorTypeImpl : KSType {
    override val annotations get() = emptySequence<Nothing>()
    override val arguments get() = emptyList<Nothing>()
    override val declaration: KSDeclaration get() = ErrorDeclarationImpl
    override val isError: Boolean get() = true
    override val isFunctionType: Boolean get() = false
    override val isMarkedNullable: Boolean get() = false
    override val isSuspendFunctionType: Boolean get() = false
    override val nullability: Nullability get() = Nullability.NOT_NULL
    override fun isAssignableFrom(that: KSType): Boolean = false
    override fun isCovarianceFlexible(): Boolean = false
    override fun isMutabilityFlexible(): Boolean = false
    override fun makeNotNullable(): KSType = this
    override fun makeNullable(): KSType = this
    override fun replace(arguments: List<KSTypeArgument>): KSType = this
    override fun starProjection(): KSType = this
}

private object ErrorDeclarationImpl : KSClassDeclaration {
    override val annotations get() = emptySequence<Nothing>()
    override val classKind: ClassKind get() = ClassKind.CLASS
    override val containingFile: Nothing? get() = null
    override val declarations get() = emptySequence<Nothing>()
    override val docString: Nothing? get() = null
    override val isActual: Boolean get() = false
    override val isCompanionObject: Boolean get() = false
    override val isExpect: Boolean get() = false
    override val location: Location get() = NonExistLocation
    override val modifiers: Set<Modifier> get() = emptySet()
    override val origin: Origin get() = Origin.SYNTHETIC
    override val packageName: KSName get() = Utils.resolver.getKSNameFromString("")
    override val parent: Nothing? get() = null
    override val parentDeclaration: Nothing? get() = null
    override val primaryConstructor: Nothing? get() = null
    override val qualifiedName: Nothing? get() = null
    override val simpleName: KSName get() = Utils.resolver.getKSNameFromString("<Error>")
    override val superTypes get() = emptySequence<Nothing>()
    override val typeParameters get() = emptyList<Nothing>()
    override fun asStarProjectedType() = ErrorTypeImpl
    override fun findActuals() = emptySequence<Nothing>()
    override fun findExpects() = emptySequence<Nothing>()
    override fun getAllFunctions() = emptySequence<Nothing>()
    override fun getAllProperties() = emptySequence<Nothing>()
    override fun getSealedSubclasses() = emptySequence<Nothing>()

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitClassDeclaration(this, data)
    }

    override fun asType(typeArguments: List<KSTypeArgument>): KSType {
        check(typeArguments.isEmpty()) { "Not reached" }
        return ErrorTypeImpl
    }
}
