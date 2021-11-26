package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getJavaClassByName
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Variance
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.ParameterLangModel
import com.yandex.daggerlite.generator.lang.ClassNameModel
import com.yandex.daggerlite.generator.lang.CtTypeNameModel
import com.yandex.daggerlite.generator.lang.ParameterizedNameModel
import com.yandex.daggerlite.generator.lang.WildcardNameModel
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

internal val KSDeclaration.isObject get() = this is KSClassDeclaration && classKind == ClassKind.OBJECT

internal val KSPropertyDeclaration.isField
    get() = origin == Origin.JAVA || origin == Origin.JAVA_LIB || isAnnotationPresent<JvmField>()

internal fun mapToJavaPlatformIfNeeded(declaration: KSClassDeclaration): KSClassDeclaration {
    if (!declaration.packageName.asString().startsWith("kotlin")) {
        // Heuristic: only types from `kotlin` package can have different java counterparts.
        return declaration
    }

    @OptIn(KspExperimental::class)
    return declaration.qualifiedName?.let(Utils.resolver::getJavaClassByName) ?: declaration
}

internal fun mapToJavaPlatformIfNeeded(type: KSType, varianceAsWildcard: Boolean = false): KSType {
    // TODO: deal with nullability here
    // MAYBE: Perf: implement caching for non-trivial mappings?
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
                    mergeVariance(declarationSite = param.variance, useSite = arg.variance)
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
    return mappedDeclaration.asType(mappedArguments)
}

private fun ClassNameModel(declaration: KSClassDeclaration): ClassNameModel {
    val packageName = declaration.packageName.asString()
    return ClassNameModel(
        packageName = packageName,
        simpleNames = declaration.qualifiedName!!.asString().substring(startIndex = packageName.length + 1)
            .split('.'),
    )
}

internal fun CtTypeNameModel(type: KSType): CtTypeNameModel {
    val declaration = type.declaration as KSClassDeclaration
    val raw = ClassNameModel(declaration)
    val typeArguments = type.arguments.map { argument ->
        fun argType() = argument.type!!.resolve()
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

private fun mergeVariance(declarationSite: Variance, useSite: Variance): Variance {
    return when (declarationSite) {
        Variance.INVARIANT -> useSite
        Variance.COVARIANT -> when (useSite) {
            Variance.INVARIANT -> Variance.COVARIANT
            Variance.COVARIANT -> Variance.COVARIANT
            Variance.CONTRAVARIANT -> throw IllegalArgumentException("variance conflict: covariant vs contravariant")
            Variance.STAR -> Variance.STAR
        }
        Variance.CONTRAVARIANT -> when (useSite) {
            Variance.INVARIANT -> Variance.CONTRAVARIANT
            Variance.CONTRAVARIANT -> Variance.CONTRAVARIANT
            Variance.COVARIANT -> throw IllegalArgumentException("variance conflict: contravariant vs covariant")
            Variance.STAR -> Variance.STAR
        }
        Variance.STAR -> throw IllegalArgumentException("'*' (star) is not a valid declaration-site variance")
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
    containing: KSType,
) = sequence<ParameterLangModel> {
    val parameters = declaration.parameters
    val types = declaration.asMemberOf(containing).parameterTypes
    for (i in parameters.indices) {
        yield(KspParameterImpl(impl = parameters[i], refinedType = types[i]!!))
    }
}