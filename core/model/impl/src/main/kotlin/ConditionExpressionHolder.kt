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
import com.yandex.yatagan.core.model.impl.parsing.BooleanExpressionParser
import com.yandex.yatagan.core.model.impl.parsing.ExpressionFactoryForParsing
import com.yandex.yatagan.core.model.impl.parsing.ParseException
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.reportError

internal class ConditionExpressionHolder(
    val impl: BuiltinAnnotation.ConditionExpression,
) {
    private val parseError: CharSequence?

    val conditionScope: ConditionScope.ExpressionScope?

    init {
        var parseError: CharSequence? = null

        val conditionScope = try {
            val expression = BooleanExpressionParser(
                expressionSource = impl.value,
                factory = ExpressionFactoryForParsing(
                    imports = buildMap {
                        for (import in impl.imports) {
                            put(import.declaration.qualifiedName.substringAfterLast('.'), import)
                        }
                        for (importAs in impl.importAs) {
                            if (!importAs.alias.matches(AliasedImportRegex)) {
                                // Skip invalid imports
                                continue
                            }
                            put(importAs.alias, importAs.value)
                        }
                    }
                ),
            ).parse()
            ConditionScopeImpl(expression)
        } catch (e: ParseException) {
            parseError = e.formattedMessage
            null
        }

        this.parseError = parseError
        this.conditionScope = conditionScope
    }

    fun validate(validator: Validator) {
        parseError?.let { parseError ->
            validator.reportError(Strings.Errors.conditionExpressionParseErrors(parseError))
        }

        listOf(
            impl.imports.map { it.declaration.qualifiedName.substringAfterLast('.') to it },
            impl.importAs.map { it.alias to it.value },
        ).flatten().groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        ).entries.forEach { (name, types) ->
            if (types.size > 1) {
                validator.reportError(Strings.Errors.conflictingConditionExpressionImport(
                    name = name,
                    types = types,
                ))
            }
        }

        for (importAs in impl.importAs) {
            val alias = importAs.alias
            if (!alias.matches(AliasedImportRegex)) {
                validator.reportError(Strings.Errors.invalidAliasedImport(alias))
            }
        }
    }

    companion object {
        private val AliasedImportRegex = """[a-zA-Z0-9_]+""".toRegex()
    }
}
