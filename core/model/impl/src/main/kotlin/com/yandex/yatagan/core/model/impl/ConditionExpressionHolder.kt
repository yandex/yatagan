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
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.reportError

internal class ConditionExpressionHolder(
    val impl: BuiltinAnnotation.ConditionExpression,
    private val referenceLoop: List<Type>?,
) {
    private val parseError: CharSequence?

    val conditionScope: ConditionScope.ExpressionScope?

    init {
        var parseError: CharSequence? = null

        val conditionScope = if (referenceLoop != null) null else try {
            val expression = BooleanExpressionParser(
                expressionSource = impl.value,
                factory = ExpressionFactoryForParsing(imports = importsOf(impl)),
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
        referenceLoop?.let { referenceLoop ->
            validator.reportError(Strings.Errors.featureReferenceLoop(chain = referenceLoop))
        }

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

        private fun importsOf(impl: BuiltinAnnotation.ConditionExpression): Map<String, Type> = buildMap {
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

        fun referencedFeatures(impl: BuiltinAnnotation.ConditionExpression): List<Type> {
            val imports = importsOf(impl)
            val features = arrayListOf<Type>()
            try {
                BooleanExpressionParser(
                    expressionSource = impl.value,
                    factory = object : BooleanExpressionParser.Factory<Unit> {
                        override fun createAnd(lhs: Unit, rhs: Unit) = Unit
                        override fun createOr(lhs: Unit, rhs: Unit) = Unit
                        override fun createNot(e: Unit) = Unit
                        override fun parseVariable(text: String): BooleanExpressionParser.Factory.ParseResult<Unit> {
                            if (text.startsWith("@")) {
                                imports[text.substring(1)]?.let(features::add)
                            }
                            return BooleanExpressionParser.Factory.ParseResult.Ok(Unit)
                        }
                    },
                ).parse()
            } catch (e: ParseException) {
                return emptyList()
            }
            return features
        }
    }
}
