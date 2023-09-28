/*
 * Copyright 2023 Yandex LLC
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

package com.yandex.yatagan.core.model.impl.parsing

import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.impl.AndExpressionImpl
import com.yandex.yatagan.core.model.impl.BooleanExpressionInternal
import com.yandex.yatagan.core.model.impl.ConditionModelImpl
import com.yandex.yatagan.core.model.impl.FeatureModelImpl
import com.yandex.yatagan.core.model.impl.OrExpressionImpl
import com.yandex.yatagan.core.model.impl.parsing.BooleanExpressionParser.Factory.ParseResult
import com.yandex.yatagan.lang.Type

internal class ExpressionFactoryForParsing(
    private val imports: Map<String, Type>,
) : BooleanExpressionParser.Factory<BooleanExpressionInternal> {
    override fun createAnd(lhs: BooleanExpressionInternal, rhs: BooleanExpressionInternal) = AndExpressionImpl(lhs, rhs)

    override fun createOr(lhs: BooleanExpressionInternal, rhs: BooleanExpressionInternal) = OrExpressionImpl(lhs, rhs)

    override fun createNot(e: BooleanExpressionInternal) = e.negate()

    override fun parseVariable(text: String): ParseResult<BooleanExpressionInternal> = when {
        text.startsWith("@") -> {
            // Feature reference
            val featureName = text.substring(1)
            val featureType = imports[featureName]
            if (featureType != null) {
                when (val scope = FeatureModelImpl(featureType.declaration).conditionScope) {
                    null -> ParseResult.Error("invalid feature reference: `$featureType` is not a feature declaration")
                    ConditionScope.Always, ConditionScope.Never ->
                        ParseResult.Error("invalid feature reference: `$featureType` is not a valid feature declaration")

                    is ConditionScope.ExpressionScope ->
                        ParseResult.Ok(scope.expression as BooleanExpressionInternal)
                }
            } else {
                ParseResult.Error("missing import for `$featureName`")
            }
        }

        "::" in text -> {
            // Qualified condition access expression
            val receiverTypeName = text.substringBefore("::")
            val receiverType = imports[receiverTypeName]
            val path = text.substringAfter("::")
            if (receiverType != null) {
                ParseResult.Ok(ConditionModelImpl(
                    type = receiverType,
                    pathSource = path,
                ))
            } else {
                ParseResult.Error("missing import for `$receiverTypeName`")
            }
        }

        else -> {
            // unqualified condition access expression
            if (imports.values.size != 1) {
                ParseResult.Error("unqualified reference is only allowed when a single import is present")
            } else {
                val receiverType = imports.values.single()
                ParseResult.Ok(ConditionModelImpl(
                    type = receiverType,
                    pathSource = text,
                ))
            }
        }
    }
}