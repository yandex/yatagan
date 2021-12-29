package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getJavaClassByName
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.NonExistLocation
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Variance
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.generator.lang.ArrayNameModel
import com.yandex.daggerlite.generator.lang.ClassNameModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import com.yandex.daggerlite.generator.lang.KeywordTypeNameModel
import com.yandex.daggerlite.generator.lang.ParameterizedNameModel
import com.yandex.daggerlite.generator.lang.WildcardNameModel
import kotlin.LazyThreadSafetyMode.NONE
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
    get() = origin == Origin.JAVA || origin == Origin.JAVA_LIB || isAnnotationPresent<JvmField>()

internal fun KSTypeReference?.resolveOrError(): KSType {
    return this?.resolve() ?: ErrorTypeImpl
}

private fun mapToJavaPlatformIfNeeded(declaration: KSClassDeclaration): KSClassDeclaration {
    if (!declaration.packageName.asString().startsWith("kotlin")) {
        // Heuristic: only types from `kotlin` package can have different java counterparts.
        return declaration
    }

    return declaration.qualifiedName?.let(Utils.resolver::getJavaClassByName) ?: declaration
}

internal fun mapToJavaPlatformIfNeeded(type: KSType, varianceAsWildcard: Boolean = false): KSType {
    // MAYBE: Perf: implement caching for non-trivial mappings?
    if (type.isError) return type
    val originalDeclaration = type.declaration as? KSClassDeclaration ?: return type
    val mappedDeclaration = mapToJavaPlatformIfNeeded(declaration = originalDeclaration)
    if (mappedDeclaration == originalDeclaration && type.arguments.isEmpty()) {
        return type
    }
    val mappedArguments = type.arguments.zip(originalDeclaration.typeParameters)
        .map(fun(argAndParam: Pair<KSTypeArgument, KSTypeParameter>): KSTypeArgument {
            val (arg, param) = argAndParam
            val originalArgType = arg.type?.resolve() ?: return arg
            val mappedArgType = mapToJavaPlatformIfNeeded(
                type = originalArgType,
                varianceAsWildcard = varianceAsWildcard,
            )
            return Utils.resolver.getTypeArgument(
                typeRef = Utils.resolver.createKSTypeReferenceFromKSType(mappedArgType),
                variance = if (varianceAsWildcard) {
                    mergeVariance(
                        declarationSite = param.variance,
                        useSite = arg.variance,
                        isTypeOpen = { mappedArgType.declaration.isOpen() },
                    )
                } else {
                    if (param.variance == arg.variance) {
                        // redundant projection
                        Variance.INVARIANT
                    } else {
                        arg.variance
                    }
                },
            )
        })
    return try {
        mappedDeclaration.asType(mappedArguments)
    } catch (e: ClassCastException) {
        // fixme: think of a better way to handle it
        type  // Internal KSP error, likely due to an ErrorType in mappedArguments, so return unmapped type.
    }
}

private fun ClassNameModel(declaration: KSClassDeclaration): ClassNameModel {
    val packageName = declaration.packageName.asString()
    return ClassNameModel(
        packageName = packageName,
        simpleNames = declaration.qualifiedName?.asString()?.substring(startIndex = packageName.length + 1)
            ?.split('.') ?: listOf("<unnamed>"),
    )
}

internal fun inferJvmInfoFrom(kotlinDeclaredType: KSType): JvmTypeInfo {
    val declaration = kotlinDeclaredType.declaration as KSClassDeclaration
    return when (declaration.qualifiedName?.asString()) {
        "kotlin.ByteArray" -> JvmTypeInfo.Array(JvmTypeInfo.Byte)
        "kotlin.IntArray" -> JvmTypeInfo.Array(JvmTypeInfo.Int)
        "kotlin.LongArray" -> JvmTypeInfo.Array(JvmTypeInfo.Long)
        "kotlin.ShortArray" -> JvmTypeInfo.Array(JvmTypeInfo.Short)
        "kotlin.FloatArray" -> JvmTypeInfo.Array(JvmTypeInfo.Float)
        "kotlin.DoubleArray" -> JvmTypeInfo.Array(JvmTypeInfo.Double)
        "kotlin.CharArray" -> JvmTypeInfo.Array(JvmTypeInfo.Char)
        "kotlin.BooleanArray" -> JvmTypeInfo.Array(JvmTypeInfo.Boolean)
        "kotlin.Array" -> JvmTypeInfo.Array(JvmTypeInfo.Declared)
        "kotlin.Unit" -> JvmTypeInfo.Void
        else -> JvmTypeInfo.Declared
    }
}

internal fun CtTypeNameModel(
    type: KSType,
    jvmTypeKind: JvmTypeInfo = inferJvmInfoFrom(type),
): CtTypeNameModel = CtTypeNameModel(jvmTypeKind = jvmTypeKind, typeSupplier = { type })

internal fun CtTypeNameModel(
    jvmTypeKind: JvmTypeInfo,
    typeSupplier: () -> KSType,
): CtTypeNameModel {
    return when (jvmTypeKind) {
        JvmTypeInfo.Void -> KeywordTypeNameModel.Void
        JvmTypeInfo.Byte -> KeywordTypeNameModel.Byte
        JvmTypeInfo.Char -> KeywordTypeNameModel.Char
        JvmTypeInfo.Double -> KeywordTypeNameModel.Double
        JvmTypeInfo.Float -> KeywordTypeNameModel.Float
        JvmTypeInfo.Int -> KeywordTypeNameModel.Int
        JvmTypeInfo.Long -> KeywordTypeNameModel.Long
        JvmTypeInfo.Short -> KeywordTypeNameModel.Short
        JvmTypeInfo.Boolean -> KeywordTypeNameModel.Boolean
        JvmTypeInfo.Declared -> {
            val type = typeSupplier()
            val declaration = type.declaration as KSClassDeclaration
            val raw = ClassNameModel(declaration)
            val typeArguments = type.arguments.map { argument ->
                fun argType() = argument.type.resolveOrError()
                when (argument.variance) {
                    Variance.STAR -> WildcardNameModel.Star
                    Variance.INVARIANT -> CtTypeNameModel(argType())
                    Variance.COVARIANT -> WildcardNameModel(upperBound = CtTypeNameModel(argType()))
                    Variance.CONTRAVARIANT -> WildcardNameModel(lowerBound = CtTypeNameModel(argType()))
                }
            }
            return if (typeArguments.isNotEmpty()) {
                ParameterizedNameModel(raw, typeArguments)
            } else raw
        }
        is JvmTypeInfo.Array -> {
            return ArrayNameModel(
                elementType = CtTypeNameModel(
                    typeSupplier = { typeSupplier().arguments.first().type.resolveOrError() },
                    jvmTypeKind = jvmTypeKind.elementInfo,
                )
            )
        }
    }
}

private inline fun mergeVariance(declarationSite: Variance, useSite: Variance, isTypeOpen: () -> Boolean): Variance {
    return when (declarationSite) {
        Variance.INVARIANT -> useSite
        Variance.COVARIANT -> when (useSite) {
            Variance.INVARIANT -> if (isTypeOpen()) Variance.COVARIANT else Variance.INVARIANT
            Variance.COVARIANT -> if (isTypeOpen()) Variance.COVARIANT else Variance.INVARIANT
            Variance.CONTRAVARIANT -> throw AssertionError("Not reached: variance conflict: covariant vs contravariant")
            Variance.STAR -> Variance.STAR
        }
        Variance.CONTRAVARIANT -> when (useSite) {
            Variance.INVARIANT -> Variance.CONTRAVARIANT
            Variance.CONTRAVARIANT -> Variance.CONTRAVARIANT
            Variance.COVARIANT -> throw AssertionError("Not reached: variance conflict: contravariant vs covariant")
            Variance.STAR -> Variance.STAR
        }
        Variance.STAR -> throw AssertionError("Not reached: '*' (star) is not a valid declaration-site variance")
    }
}

internal fun KSClassDeclaration.getCompanionObject(): KSClassDeclaration? =
    declarations.filterIsInstance<KSClassDeclaration>().find(KSClassDeclaration::isCompanionObject)

internal fun KSClassDeclaration.allPublicFunctions(): Sequence<KSFunctionDeclaration> {
    return sequenceOf(
        getAllFunctions(),
        getDeclaredFunctions().filter { Modifier.JAVA_STATIC in it.modifiers },
    ).flatten().filter { it.isPublic() && !it.isConstructor() }
}

internal fun KSClassDeclaration.allPublicProperties(): Sequence<KSPropertyDeclaration> {
    return getAllProperties().filter(KSPropertyDeclaration::isPublic)
}

internal fun annotationsFrom(impl: KSAnnotated) = impl.annotations.map(::KspAnnotationImpl).memoize()

internal fun parametersSequenceFor(
    declaration: KSFunctionDeclaration,
    jvmMethodSignature: JvmMethodSignature,
    containing: KSType,
) = sequence<ParameterLangModel> {
    val parameters = declaration.parameters
    val types = declaration.asMemberOf(containing).parameterTypes
    for (i in parameters.indices) {
        yield(KspParameterImpl(
            impl = parameters[i],
            jvmSignatureSupplier = { jvmMethodSignature.parameterTypes?.get(i) },
            refinedType = types[i] ?: ErrorTypeImpl,
        ))
    }
}

/**
 * This is required for correctly distinguish between Java's primitive and wrapper types, as they all are
 * represented uniformly in Kotlin.
 */
internal class JvmMethodSignature(
    declaration: KSFunctionDeclaration,
) {
    private val match by lazy(NONE) {
        Utils.resolver.mapToJvmSignature(declaration)?.let { descriptor ->
            checkNotNull(MethodSignatureRegex.matchEntire(descriptor)) {
                "Not reached: invalid jvm method signature: $descriptor"
            }
        }
    }

    val returnType: String? by lazy(NONE) {
        match?.groupValues?.get(2)
    }

    val parameterTypes: List<String>? by lazy(NONE) {
        match?.groupValues?.get(1)?.let { params ->
            ParamSignatureRegex.findAll(params).map(MatchResult::value).toList()
        }
    }

    companion object {
        private val MethodSignatureRegex = """^\((.*)\)(.*)$""".toRegex()
        private val ParamSignatureRegex = """\[*(?:B|C|D|F|I|J|S|Z|L.*?;)""".toRegex()
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