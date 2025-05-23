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

package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.ConditionalHoldingModel
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.lang.scope.caching
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendRichString
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError
import com.yandex.yatagan.validation.format.toString

internal class FeatureModelImpl private constructor(
    private val impl: TypeDeclaration,
) : ConditionalHoldingModel.FeatureModel {
    override val conditionScope: ConditionScope?
        get() = conditionExpressionHolder?.conditionScope ?: legacyConditionScope

    private val legacyConditionScope: ConditionScope? by lazy {
        val annotations = impl.getAnnotations(BuiltinAnnotation.ConditionFamily)
        if (annotations.isEmpty()) {
            null
        } else {
            ConditionScopeImpl(annotations.map { conditionModel ->
                when (conditionModel) {
                    is BuiltinAnnotation.ConditionFamily.Any -> {
                        val clause = conditionModel.conditions.map {
                            parseOneCondition(it)
                        }
                        if (clause.isEmpty()) {
                            // If at least one or-clause is empty, then it's a never-scope.
                            // It's not a valid usage and will be reported, but we have to handle it here anyway.
                            return@lazy ConditionScope.Never
                        }
                        clause.reduce { expression, variable ->
                            OrExpressionImpl(
                                lhs = expression,
                                rhs = variable,
                            )
                        }
                    }

                    is BuiltinAnnotation.ConditionFamily.One ->
                        parseOneCondition(conditionModel)
                }
            }.reduce { expression, clause ->
                AndExpressionImpl(
                    lhs = expression,
                    rhs = clause,
                )
            })
        }
    }

    private val conditionExpressionHolder: ConditionExpressionHolder? by lazy {
        impl.getAnnotation(BuiltinAnnotation.ConditionExpression)?.let { ConditionExpressionHolder(it) }
    }

    private fun parseOneCondition(one: BuiltinAnnotation.ConditionFamily.One): BooleanExpressionInternal {
        val condition = one.condition
        return if (condition.firstOrNull() == '!') {
            ConditionModelImpl(one.target, condition.substring(1)).negate()
        } else {
            ConditionModelImpl(one.target, condition)
        }
    }

    override fun validate(validator: Validator) {
        val hasLegacyConditions = hasLegacyConditions()
        val hasNewConditions = hasConditionExpression()
        if (!hasLegacyConditions && !hasNewConditions) {
            validator.reportError(Strings.Errors.noConditionsOnFeature())
        }
        if (hasLegacyConditions && hasNewConditions) {
            validator.reportError(Strings.Errors.conflictingConditionsOnFeature())
        }
        conditionScope?.let { conditionScope ->
            for (model in conditionScope.allConditionModels().toSet()) {
                validator.child(model)
            }
        }
        conditionExpressionHolder?.validate(validator)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "feature",
        representation = {
            append(impl)
            append(' ')
            when(val conditionScope = conditionScope) {
                null -> appendRichString {
                    color = TextColor.Red
                    if (!hasLegacyConditions() && !hasConditionExpression()) {
                        append("<no-conditions-declared>")
                    } else {
                        append("<invalid-condition-expression>")
                    }
                }
                ConditionScope.Always -> appendRichString {
                    color = TextColor.Red
                    append("<illegal-always>")
                }
                ConditionScope.Never -> appendRichString {
                    color = TextColor.Red
                    append("<illegal-never>")
                }
                else -> append(conditionScope.toString(childContext = childContext))
            }
        },
    )

    override val type: Type
        get() = impl.asType()

    private fun hasLegacyConditions(): Boolean =
        impl.getAnnotations(BuiltinAnnotation.ConditionFamily).isNotEmpty()

    private fun hasConditionExpression(): Boolean =
        conditionExpressionHolder != null

    companion object Factory : FactoryKey<TypeDeclaration, FeatureModelImpl> {
        override fun LexicalScope.factory() = caching(::FeatureModelImpl)
    }
}
