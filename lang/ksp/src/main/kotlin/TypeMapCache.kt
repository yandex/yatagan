package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getJavaClassByName
import com.google.devtools.ksp.getKotlinClassByName
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance
import com.yandex.daggerlite.base.BiObjectCache
import com.yandex.daggerlite.base.ObjectCache

/**
 * Object cache for type normalization mapping.
 *
 * @see [TypeMapCache.normalizeType].
 */
internal object TypeMapCache : BiObjectCache<Boolean, KSType, KSType>() {
    private fun mapDeclarationToJavaPlatformIfNeeded(declaration: KSClassDeclaration): KSClassDeclaration {
        if (!declaration.packageName.asString().startsWith("kotlin")) {
            // Heuristic: only types from `kotlin` package can have different java counterparts.
            return declaration
        }

        return declaration.qualifiedName?.let(Utils.resolver::getJavaClassByName) ?: declaration
    }

    private fun computeWildcard(
        declarationSite: Variance,
        projection: Variance,
        forType: KSType,
    ): Variance {
        return when (declarationSite) {
            Variance.INVARIANT -> projection
            Variance.COVARIANT -> when (projection) {
                Variance.INVARIANT -> if (forType.declaration.isOpen()) Variance.COVARIANT else Variance.INVARIANT
                Variance.COVARIANT -> if (forType.declaration.isOpen()) Variance.COVARIANT else Variance.INVARIANT
                Variance.CONTRAVARIANT -> throw AssertionError("Not reached: covariant is projected as contravariant")
                Variance.STAR -> Variance.STAR
            }
            Variance.CONTRAVARIANT -> when (projection) {
                Variance.INVARIANT -> Variance.CONTRAVARIANT
                Variance.CONTRAVARIANT -> Variance.CONTRAVARIANT
                Variance.COVARIANT -> throw AssertionError("Not reached: contravariant is projected as covariant")
                Variance.STAR -> Variance.STAR
            }
            Variance.STAR -> throw AssertionError("Not reached: '*' (star) is not a variance spec")
        }
    }

    private fun doNormalizeType(type: KSType, bakeVarianceAsWildcard: Boolean): KSType {
        // Early bail out for error types, as the following code is likely to fail with them
        if (type.isError) return type

        val originalDeclaration = type.getNonAliasDeclaration() ?: return type
        val mappedDeclaration = mapDeclarationToJavaPlatformIfNeeded(declaration = originalDeclaration)

        // Early return, if no further mapping needed
        if (mappedDeclaration == originalDeclaration && type.arguments.isEmpty()) {
            return type.makeNotNullable()
        }

        // Map type arguments, recursively
        // NOTE: This routine includes logic which ideally is handled by `Resolver.getJavaWildcard`, yet we don't use
        //  that routine here, as it works incorrectly in cases crucial to Dagger. So until it is fixed, we use custom
        //  "lite" implementation here, which suits our needs (hopefully).
        //  Issue list for `Resolver.getJavaWildcard` (not limited to):
        //  - https://github.com/google/ksp/issues/774
        //  - https://github.com/google/ksp/issues/773
        //  - https://github.com/google/ksp/issues/772
        val mappedArguments = type.arguments.zip(originalDeclaration.typeParameters)
            .map { (arg: KSTypeArgument, param: KSTypeParameter) ->
                val argTypeRef = arg.type ?: return@map arg
                val mappedArgType = normalizeTypeImpl(
                    type = argTypeRef.resolve(),
                    bakeVarianceAsWildcard = bakeVarianceAsWildcard,
                )
                if (mappedArgType.isError) {
                    // Bail out of the mapping, as error type is present - it's known to cause crashes inside
                    // KSClassDeclaration.asType() invocation
                    return type
                }
                Utils.resolver.getTypeArgument(
                    typeRef = argTypeRef.replaceType(mappedArgType),
                    variance = if (bakeVarianceAsWildcard) {
                        computeWildcard(
                            declarationSite = param.variance,
                            projection = arg.variance,
                            forType = mappedArgType,
                        )
                    } else {
                        // explicit variance
                        arg.variance
                    },
                )
            }
        return mappedDeclaration.asType(mappedArguments).makeNotNullable()
    }

    private fun normalizeTypeImpl(
        type: KSType,
        bakeVarianceAsWildcard: Boolean,
    ): KSType = createCached(bakeVarianceAsWildcard, type) {
        doNormalizeType(type, bakeVarianceAsWildcard)
    }

    private fun KSTypeReference.shouldSuppressWildcards(): Boolean {
        val suppress = getAnnotationsByType(JvmSuppressWildcards::class)
            .fold(false) { acc, ann -> acc || ann.suppress } ||
                element?.typeArguments?.any { it.type?.shouldSuppressWildcards() == true } ?: false
        return when (val declaration = this.resolve().declaration) {
            is KSTypeAlias -> suppress || declaration.type.shouldSuppressWildcards()
            else -> suppress
        }
    }

    private fun KSTypeReference.shouldForceWildcards(): Boolean {
        val force = isAnnotationPresent<JvmWildcard>() ||
                element?.typeArguments?.any { it.type?.shouldForceWildcards() == true } ?: false
        return when (val declaration = this.resolve().declaration) {
            is KSTypeAlias -> force || declaration.type.shouldForceWildcards()
            else -> force
        }
    }

    /**
     * Maps the type from Kotlin-specific type system to Java-specific type system, recursively.
     * Discards nullability info, recursively.
     * Simulates Java wildcards from parameterized types using [Variance] based on rules Kotlin has for Java
     * compatibility.
     *
     * @param reference type reference to resolve and normalized. Reference is used as it may contain annotations that
     * may alter wildcard mapping process.
     * @param position type position to control whether wildcard mapping is required.
     */
    fun normalizeType(
        reference: KSTypeReference,
        position: Position,
    ): KSType {
        val originalType = reference.resolve()
        val type = normalizeTypeImpl(
            type = originalType,
            bakeVarianceAsWildcard = when (position) {
                Position.Parameter -> !reference.shouldSuppressWildcards()
                Position.Other -> reference.shouldForceWildcards()
            },
        )
        if (reference.isFromJava) {
            // Optimize for java code - no need to go through full normalize routine - no kotlin type could be
            // referenced from java

            // Try raw type detection
            if ((type.declaration as? KSClassDeclaration)?.typeParameters?.isNotEmpty() == true &&
                "raw " in originalType.toString()
            ) {
                // Raw type detected - remove synthetic arguments, that were "conveniently" added by KSP.
                return RawType.of(type)
            }
        }
        return type
    }

    /**
     * A version of [normalizeType] for cases where no context info ([KSTypeReference]/[Position]) is available.
     * See [normalizeType].
     */
    fun normalizeType(
        type: KSType,
    ): KSType = normalizeTypeImpl(
        type = type,
        bakeVarianceAsWildcard = false,
    )

    /**
     * Inverse operation to [normalizeType]. The two-way conversion is not lossless though.
     *
     * @return Kotlin-specific counterpart of the given type.
     */
    fun mapToKotlinType(
        type: KSType,
    ): KSType {
        val declaration = type.getNonAliasDeclaration()
        val qualifiedName = declaration?.qualifiedName ?: return type
        if (!qualifiedName.asString().startsWith("java")) {
            // Only java.* types may have kotlin counterparts.
            return type
        }
        return Utils.resolver.getKotlinClassByName(qualifiedName)?.asType(
            type.arguments.map { arg ->
                when (val typeRef = arg.type) {
                    null -> arg
                    else -> Utils.resolver.getTypeArgument(
                        typeRef = typeRef.replaceType(mapToKotlinType(typeRef.resolve())),
                        variance = arg.variance,
                    )
                }
            }
        ) ?: type
    }

    /**
     * The nature of a typed construct in a syntactic tree.
     */
    enum class Position {
        /**
         * Type is used a parameter type.
         */
        Parameter,

        /**
         * Type is used anywhere else but parameter type.
         */
        Other,
    }

    /**
     * Wrapper around [KSType] which simulates a Java's raw type by returning empty list from [arguments].
     * This is required as [KSType.replace(emptyList())][KSType.replace] does not really remove type arguments, it
     * replaces them with their bounds instead.
     */
    internal class RawType private constructor(
        val underlying: KSType,
    ) : KSType by underlying {
        override val arguments: List<Nothing> get() = emptyList()
        override val isMarkedNullable: Boolean get() = false
        override val nullability: Nullability get() = Nullability.NOT_NULL
        override fun makeNotNullable(): KSType = this
        override fun makeNullable(): KSType = throw UnsupportedOperationException()

        companion object Factory : ObjectCache<KSType, KSType>() {
            fun of(type: KSType) = createCached(type, ::RawType)
        }
    }
}
