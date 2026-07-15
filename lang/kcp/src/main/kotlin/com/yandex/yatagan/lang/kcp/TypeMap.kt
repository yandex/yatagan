/*
 * Copyright 2026 Yandex LLC
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

@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package com.yandex.yatagan.lang.kcp

import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.compiled.CtErrorType
import com.yandex.yatagan.lang.compiled.InvalidNameModel
import com.yandex.yatagan.lang.scope.LexicalScope
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.IrErrorType
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrStarProjection
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.IrTypeSystemContext
import org.jetbrains.kotlin.ir.types.SimpleTypeNullability
import org.jetbrains.kotlin.ir.types.impl.buildSimpleType
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.types.impl.toBuilder
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

internal fun LexicalScope.kcpType(
    type: IrType,
    isTypeArgument: Boolean = false,
    position: TypePosition = TypePosition.Other,
): Type {
    return when (type) {
        is IrSimpleType -> when (val classifier = type.classifier) {
            is IrClassSymbol -> {
                // Mirrors `lang/ksp` TypeMap: parameter positions bake declaration-site variance into
                // Java wildcards (unless suppressed), other positions keep use-site projections only.
                val bakeVarianceAsWildcards =
                    position == TypePosition.Parameter && !type.shouldSuppressWildcards()
                KcpTypeImpl(KcpTypeKey(
                    type = type.normalizeNullabilityRecursively().bakeVarianceAsWildcards(
                        bake = bakeVarianceAsWildcards,
                        dropRedundantProjections = position != TypePosition.Synthetic,
                    ),
                    jvmKind = if (isTypeArgument) JvmTypeKind.Declared else type.jvmTypeKind(),
                ))
            }
            is IrTypeParameterSymbol -> CtErrorType(
                InvalidNameModel.TypeVariable(classifier.owner.name.asString()),
            )
            else -> CtErrorType(InvalidNameModel.Unresolved(type.toString()))
        }
        is IrErrorType, is IrDynamicType -> CtErrorType(InvalidNameModel.Unresolved(type.toString()))
    }
}

/**
 * The nature of a typed construct in a syntactic tree, see `lang/ksp`'s `TypeMap.Position`.
 */
internal enum class TypePosition {
    Parameter,
    Other,

    /** Factory-constructed types: use-site projections are intentional, never normalized. */
    Synthetic,
}

private fun IrSimpleType.bakeVarianceAsWildcards(
    bake: Boolean,
    dropRedundantProjections: Boolean,
): IrSimpleType {
    val declaration = (classifier as? IrClassSymbol)?.owner
    if (arguments.isEmpty() || declaration == null) return this
    val parameters = declaration.typeParameters
    val bakedArguments = arguments.mapIndexed { index, argument ->
        when (argument) {
            is IrStarProjection -> argument
            is IrTypeProjection -> {
                val argumentType = argument.type
                // Baking does not propagate into nested type arguments (only explicit @JvmWildcard does).
                val bakedArgumentType = (argumentType as? IrSimpleType)
                    ?.bakeVarianceAsWildcards(bake = false, dropRedundantProjections = dropRedundantProjections)
                    ?: argumentType
                val declarationSiteVariance = parameters.getOrNull(index)?.variance
                val variance = when {
                    (bake || argumentType.shouldForceWildcards()) && declarationSiteVariance != null ->
                        computeWildcard(
                            declarationSite = declarationSiteVariance,
                            projection = argument.variance,
                            forType = bakedArgumentType,
                        )

                    // Redundant explicit projection (e.g. `Set<out T>` where `T` is declared `out`)
                    // is dropped in the Java view of the type.
                    dropRedundantProjections && argument.variance != Variance.INVARIANT &&
                            argument.variance == declarationSiteVariance -> Variance.INVARIANT

                    else -> argument.variance
                }
                makeTypeProjection(bakedArgumentType, variance)
            }
        }
    }
    return toBuilder().apply { arguments = bakedArguments }.buildSimpleType()
}

private fun computeWildcard(
    declarationSite: Variance,
    projection: Variance,
    forType: IrType,
): Variance {
    return when (declarationSite) {
        Variance.INVARIANT -> projection
        Variance.OUT_VARIANCE -> when (projection) {
            Variance.INVARIANT, Variance.OUT_VARIANCE ->
                if (forType.isOpenClassType()) Variance.OUT_VARIANCE else Variance.INVARIANT
            // Malformed projection (only expressible from Java sources) - keep as is.
            Variance.IN_VARIANCE -> projection
        }
        Variance.IN_VARIANCE -> when (projection) {
            Variance.INVARIANT, Variance.IN_VARIANCE -> Variance.IN_VARIANCE
            // Malformed projection (only expressible from Java sources) - keep as is.
            Variance.OUT_VARIANCE -> projection
        }
    }
}

/**
 * Nullability doesn't exist in the Java view of types: Java supertypes carry flexible arguments
 * (`Supplier<String!>`, approximated in IR to `String?` + an annotation) that must stay
 * assignable to Kotlin's `Supplier<String>` even in invariant positions - anywhere in the
 * supertype chain, which erasing the compared types alone cannot reach. So the whole subtype
 * check runs nullability-blind.
 */
internal class JavaViewTypeSystemContext(
    override val irBuiltIns: IrBuiltIns,
) : IrTypeSystemContext {
    override fun KotlinTypeMarker.isMarkedNullable(): Boolean = false
}

private fun IrType.isOpenClassType(): Boolean {
    return when (val classifier = (this as? IrSimpleType)?.classifier) {
        is IrClassSymbol -> classifier.owner.modality != Modality.FINAL
        else -> true
    }
}

internal fun IrSimpleType.eraseMutability(irBuiltIns: IrBuiltIns): IrSimpleType {
    val readonly = when (classifier) {
        irBuiltIns.mutableCollectionClass -> irBuiltIns.collectionClass
        irBuiltIns.mutableListClass -> irBuiltIns.listClass
        irBuiltIns.mutableSetClass -> irBuiltIns.setClass
        irBuiltIns.mutableMapClass -> irBuiltIns.mapClass
        irBuiltIns.mutableIterableClass -> irBuiltIns.iterableClass
        else -> null
    }
    val erasedArguments = arguments.map { argument ->
        when (argument) {
            is IrStarProjection -> argument
            is IrTypeProjection -> {
                // Nullability doesn't exist in the Java view either: a Java supertype like
                // `Supplier<String!>` must stay assignable to Kotlin's `Supplier<String>`
                // even in invariant argument positions.
                val erased = ((argument.type as? IrSimpleType)?.eraseMutability(irBuiltIns)
                    ?: argument.type).makeNotNull()
                if (erased === argument.type) argument else makeTypeProjection(erased, argument.variance)
            }
        }
    }
    if (readonly == null && erasedArguments == arguments) return this
    return toBuilder().apply {
        readonly?.let { classifier = it }
        arguments = erasedArguments
    }.buildSimpleType()
}

private val RawTypeAnnotationFqName = StandardClassIds.Annotations.RawTypeAnnotation.asSingleFqName()

internal fun IrSimpleType.isRawJavaType(): Boolean =
    annotations.any { it.isAnnotation(RawTypeAnnotationFqName) }

private val JvmSuppressWildcardsFqName = FqName("kotlin.jvm.JvmSuppressWildcards")
private val JvmWildcardFqName = FqName("kotlin.jvm.JvmWildcard")

private fun IrType.shouldSuppressWildcards(): Boolean {
    val suppressedHere = annotations.any { annotation ->
        annotation.isAnnotation(JvmSuppressWildcardsFqName) &&
                ((annotation.arguments.getOrNull(0) as? IrConst)?.value as? Boolean ?: true)
    }
    return suppressedHere || (this as? IrSimpleType)?.arguments?.any { argument ->
        (argument as? IrTypeProjection)?.type?.shouldSuppressWildcards() == true
    } == true
}

private fun IrType.shouldForceWildcards(): Boolean {
    return annotations.any { it.isAnnotation(JvmWildcardFqName) }
}

private fun IrConstructorCall.isAnnotation(fqName: FqName): Boolean {
    return symbol.owner.parentAsClass.fqNameWhenAvailable == fqName
}

internal fun IrSimpleType.normalizeNullabilityRecursively(): IrSimpleType {
    val normalizedArguments = arguments.map { argument ->
        when (argument) {
            is IrStarProjection -> argument
            is IrTypeProjection -> makeTypeProjection(
                argument.type.normalizeNullabilityRecursively(),
                argument.variance,
            )
        }
    }
    return (makeNotNull() as IrSimpleType).toBuilder().apply {
        nullability = SimpleTypeNullability.DEFINITELY_NOT_NULL
        arguments = normalizedArguments
    }.buildSimpleType()
}

private fun IrType.normalizeNullabilityRecursively(): IrType {
    return when (this) {
        is IrSimpleType -> normalizeNullabilityRecursively()
        is IrErrorType, is IrDynamicType -> this
    }
}
