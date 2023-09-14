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

package com.yandex.yatagan.core.model.impl

import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.HasNodeModel
import com.yandex.yatagan.core.model.InjectedConditionExpressionModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendRichString
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

internal class InjectedConditionExpressionModelImpl(
    private val node: NodeModel,
) : InjectedConditionExpressionModel {
    private val valueOf = checkNotNull(node.qualifier?.asBuiltin(BuiltinAnnotation.ValueOf))
    private val conditionExpressionHolder by lazy { ConditionExpressionHolder(valueOf.value) }

    override val type: Type
        get() = node.type

    override fun asNode(): NodeModel = node

    override fun <R> accept(visitor: HasNodeModel.Visitor<R>): R {
        return visitor.visitConditionExpression(this)
    }

    override val expression: ConditionScope.ExpressionScope?
        get() = conditionExpressionHolder.conditionScope

    override fun validate(validator: Validator) {
        if (node.type.asBoxed().declaration.qualifiedName != Names.Boolean) {
            validator.reportError(Strings.Errors.injectedConditionExpectedBoolean(got = node.type))
        }
        conditionExpressionHolder.validate(validator)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "injectable condition expression",
        representation = {
            append("value: ").append(node.type).append(" = ")
            when (val expression = expression) {
                null -> appendRichString {
                    color = TextColor.Red
                    append("<invalid-expression>")
                }
                else -> append(expression)
            }
        },
    )

    companion object {
        fun canRepresent(qualifier: Annotation): Boolean {
            return qualifier.asBuiltin(BuiltinAnnotation.ValueOf) != null
        }
    }
}
