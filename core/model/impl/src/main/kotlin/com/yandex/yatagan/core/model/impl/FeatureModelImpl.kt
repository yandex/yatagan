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
        get() = conditionExpressionHolder?.conditionScope

    private val conditionExpressionHolder: ConditionExpressionHolder? by lazy {
        impl.getAnnotation(BuiltinAnnotation.ConditionExpression)?.let { ConditionExpressionHolder(it) }
    }

    override fun validate(validator: Validator) {
        if (conditionExpressionHolder == null) {
            validator.reportError(Strings.Errors.noConditionsOnFeature())
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
                    if (conditionExpressionHolder == null) {
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

    companion object Factory : FactoryKey<TypeDeclaration, FeatureModelImpl> {
        override fun LexicalScope.factory() = caching(::FeatureModelImpl)
    }
}
