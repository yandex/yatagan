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

import com.yandex.yatagan.base.ObjectCache
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.ConditionalHoldingModel
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
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
    override val conditionScope: ConditionScope by lazy {
        val annotations = impl.getAnnotations(BuiltinAnnotation.ConditionFamily)
        if (annotations.isEmpty()) {
            ConditionScope.Always
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

    private fun parseOneCondition(one: BuiltinAnnotation.ConditionFamily.One): BooleanExpressionInternal {
        val match = ConditionRegex.matchEntire(one.condition)
        return if (match != null) {
            val (negate, names) = match.destructured
            val variable = ConditionModelImpl(
                type = one.target,
                pathSource = names,
            )
            if (negate.isNotEmpty()) {
                NotExpressionImpl(variable)
            } else variable
        } else {
            ConditionModelImpl.Invalid(
                type = one.target,
                invalidExpression = one.condition,
            )
        }
    }

    override fun validate(validator: Validator) {
        if (impl.getAnnotations(BuiltinAnnotation.ConditionFamily).none()) {
            // TODO: Forbid Never-scope/Always-scope.
            validator.reportError(Strings.Errors.noConditionsOnFeature())
        }
        for (model in conditionScope.allConditionModels().toSet()) {
            validator.child(model)
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "feature",
        representation = {
            append(impl)
            append(' ')
            when(conditionScope) {
                ConditionScope.Always -> appendRichString {
                    color = TextColor.Red
                    append("<no-conditions-declared>")
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

    companion object Factory : ObjectCache<TypeDeclaration, FeatureModelImpl>() {
        operator fun invoke(impl: TypeDeclaration) = createCached(impl, ::FeatureModelImpl)

        private val ConditionRegex = "^(!?)((?:[A-Za-z][A-Za-z0-9_]*\\.)*[A-Za-z][A-Za-z0-9_]*)\$".toRegex()
    }
}
