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

package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getJavaClassByName
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance
import com.yandex.yatagan.lang.ksp.TypeMap.normalizeType
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.lang.scope.caching

internal object TypeMap {
    private fun LexicalScope.mapDeclarationToJavaPlatformIfNeeded(declaration: KSClassDeclaration): KSClassDeclaration {
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

    private fun infuseReference(
        forType: KSTypeReference?,
        forNature: KSTypeReference?,
    ): KSTypeReference? {
        if (forNature == null)
            return forType
        if (forType == null)
            return forNature
        return forNature.replaceType(forType)
    }

    private fun LexicalScope.doNormalizeType(
        typeReference: KSTypeReference,
        bakeVarianceAsWildcard: Boolean,
    ): KSType {
        val originalType = typeReference.resolve()

        // Early bail out for error types, as the following code is likely to fail for KSP1 with them
        if (originalType.isError && !Utils.isKsp2) return originalType

        // TODO: Support parameterized type-aliases, now broken
        val type = originalType.resolveAliasIfNeeded()
        val originalDeclaration = type.declaration as? KSClassDeclaration ?: return type
        val mappedDeclaration = mapDeclarationToJavaPlatformIfNeeded(declaration = originalDeclaration)

        // Early return, if no further mapping needed
        if (mappedDeclaration == originalDeclaration && type.arguments.isEmpty()) {
            return type.makeNotNullable()
        }

        // Map type arguments, recursively
        // NOTE: This routine includes logic which ideally is handled by `Resolver.getJavaWildcard`, yet we don't use
        //  that routine here, as we need to tweak the process.
        val literalTypeArguments = typeReference.element?.typeArguments
        val mappedArguments = type.arguments.zip(originalDeclaration.typeParameters)
            .mapIndexed { index: Int, (arg: KSTypeArgument, param: KSTypeParameter) ->
                // Argument, that as it was written in code. Type doesn't matter here, as it may be type variable,
                //  and it is already resolved in `arg`. But the context (nature) of the reference is preserved.
                val literalArg = literalTypeArguments?.getOrNull(index)

                // Resolve actual projection either from usage or from type
                val argVariance = (literalArg ?: arg).variance

                // Combine an "infused" reference - a reference with the original context, yet with the refined type.
                val argTypeReference = infuseReference(
                    forType = arg.type,
                    forNature = literalArg?.type,
                ) ?: return@mapIndexed arg

                // Normalize the type argument recursively via the combined reference.
                val mappedArgType = normalizeTypeImpl(
                    typeReference = argTypeReference,
                    bakeVarianceAsWildcard = false,  // Doesn't propagate for type parameters
                )
                if (mappedArgType.isError && !Utils.isKsp2) {
                    // Bail out of the mapping, as error type is present - it's known to cause crashes inside
                    // KSClassDeclaration.asType() invocation
                    return type
                }
                val shouldComputeWildcard = bakeVarianceAsWildcard || argTypeReference.shouldForceWildcards()
                Utils.resolver.getTypeArgument(
                    typeRef = argTypeReference.replaceType(mappedArgType),
                    variance = if (shouldComputeWildcard) {
                        // Use declaration-site variance
                        computeWildcard(
                            declarationSite = param.variance,
                            projection = argVariance,
                            forType = mappedArgType,
                        )
                    } else {
                        // Use only use-site (literal or type) variance (projection).
                        if (literalArg?.variance == param.variance && typeReference.isFromKotlin) {
                            // Redundant explicit projection for Kotlin, needs to be handled specifically
                            Variance.INVARIANT
                        } else argVariance
                    },
                )
            }
        return mappedDeclaration.asType(mappedArguments).makeNotNullable()
    }

    private fun LexicalScope.normalizeTypeImpl(
        typeReference: KSTypeReference,
        bakeVarianceAsWildcard: Boolean,
    ): KSType = doNormalizeType(
        typeReference = typeReference,
        bakeVarianceAsWildcard = bakeVarianceAsWildcard,
    )

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
        val force = isAnnotationPresent<JvmWildcard>()
        if (force) return true
        return when (val declaration = this.resolve().declaration) {
            is KSTypeAlias -> declaration.type.shouldForceWildcards()
            else -> false
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
    fun LexicalScope.normalizeType(
        reference: KSTypeReference,
        position: Position,
    ): KSType {
        val type = normalizeTypeImpl(
            typeReference = reference,
            bakeVarianceAsWildcard = when (position) {
                Position.Parameter -> !reference.shouldSuppressWildcards()
                Position.Other -> reference.shouldForceWildcards()
            },
        )
        if (reference.isFromJava) {
            // Optimize for java code - no need to go through full normalize routine - no kotlin type could be
            // referenced from java

            if (isRaw(reference.resolve())) {
                // Raw type detected - remove synthetic arguments, that were "conveniently" added by KSP.
                return RawType(type)
            }
        }
        return type
    }

    /**
     * A version of [normalizeType] for cases where no context info ([KSTypeReference]/[Position]) is available.
     * See [normalizeType].
     */
    fun LexicalScope.normalizeType(
        type: KSType,
    ): KSType = normalizeTypeImpl(
        typeReference = asReference(type),
        bakeVarianceAsWildcard = false,
    )

    /**
     * Inverse operation to [normalizeType]. The two-way conversion is not lossless though.
     *
     * @return Kotlin-specific counterpart of the given type.
     */
    fun LexicalScope.mapToKotlinType(
        type: KSType,
    ): KSType {
        val declaration = type.classDeclaration()
        val qualifiedName = declaration?.qualifiedName ?: return type
        if (qualifiedName.asString().run { !startsWith("java.") && !startsWith("kotlin.jvm.") }) {
            // Only these types may have kotlin-specific counterparts.
            return RawType.unwrap(type)
        }
        return Utils.resolver.getKotlinClassByName(qualifiedName, forceMutable = true)?.asType(
            type.arguments.map { arg ->
                when (val typeRef = arg.type) {
                    null -> arg
                    else -> Utils.resolver.getTypeArgument(
                        typeRef = typeRef.replaceType(mapToKotlinType(typeRef.resolve())),
                        variance = arg.variance,
                    )
                }
            }
        ) ?: RawType.unwrap(type)
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
        private val underlying: KSType,
    ) : KSType by underlying {
        override val arguments: List<Nothing> get() = emptyList()
        override val isMarkedNullable: Boolean get() = false
        override val nullability: Nullability get() = Nullability.NOT_NULL
        override fun makeNotNullable(): KSType = this
        override fun makeNullable(): KSType = throw UnsupportedOperationException()

        companion object Factory : FactoryKey<KSType, RawType> {
            override fun LexicalScope.factory() = caching(::RawType)

            fun unwrap(type: KSType): KSType = when (type) {
                is RawType -> type.underlying
                else -> type
            }
        }
    }
}
