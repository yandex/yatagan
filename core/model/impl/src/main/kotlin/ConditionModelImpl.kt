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

import com.yandex.yatagan.base.BiObjectCache
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.isKotlinObject
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.ErrorMessage
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.WarningMessage
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendRichString
import com.yandex.yatagan.validation.format.buildRichString
import com.yandex.yatagan.validation.format.reportError
import com.yandex.yatagan.validation.format.reportWarning

private typealias ValidationReport = (Validator) -> Unit

private class SimpleErrorReport(val error: ErrorMessage): ValidationReport {
    override fun invoke(validator: Validator) = validator.reportError(error)
}

private class SimpleWarningReport(val warning: WarningMessage): ValidationReport {
    override fun invoke(validator: Validator) = validator.reportWarning(warning)
}

private class CompositeErrorReport(
    val base: ValidationReport?,
    val added: ValidationReport,
) : ValidationReport {
    override fun invoke(validator: Validator) {
        base?.invoke(validator)
        added.invoke(validator)
    }
}

private object MemberTypeVisitor : Member.Visitor<Type> {
    override fun visitMethod(model: Method) = model.returnType
    override fun visitField(model: Field) = model.type
}

internal class ConditionModelImpl private constructor(
    private val type: Type,
    override val pathSource: String,
) : VariableBaseImpl() {
    private var validationReport: ValidationReport? = null
    private var _nonStatic = false

    override val root: NodeModel
        get() = NodeModelImpl(
            type = type,
            qualifier = null,
        )

    override val path: List<Member> by lazy {
        buildList {
            var currentType = type
            var finished = false

            var isFirst = true
            pathSource.split('.').forEach { name ->
                if (finished) {
                    validationReport = SimpleErrorReport(Strings.Errors.invalidConditionNoBoolean())
                    return@forEach
                }

                val currentDeclaration = currentType.declaration
                if (!currentDeclaration.isEffectivelyPublic) {
                    validationReport = SimpleErrorReport(
                        Strings.Errors.invalidAccessForConditionClass(`class` = currentDeclaration))
                    return@buildList
                }

                val member = findAccessor(currentDeclaration, name)
                if (member == null) {
                    validationReport = SimpleErrorReport(
                        Strings.Errors.invalidConditionMissingMember(name = name, type = currentType))
                    return@buildList
                }
                if (isFirst) {
                    if (!member.isStatic) {
                        if (currentType.declaration.isKotlinObject) {
                            // Issue warning
                            validationReport = SimpleWarningReport(Strings.Warnings.nonStaticConditionOnKotlinObject())
                        }
                        _nonStatic = true
                    }
                    isFirst = false
                }
                if (!member.isEffectivelyPublic) {
                    validationReport = SimpleErrorReport(
                        Strings.Errors.invalidAccessForConditionMember(member = member))
                    return@buildList
                }
                add(member)

                val type = member.accept(MemberTypeVisitor)
                if (type.asBoxed().declaration.qualifiedName == Names.Boolean) {
                    finished = true
                } else {
                    currentType = type
                }
            }
            if (!finished) {
                validationReport = CompositeErrorReport(
                    base = validationReport,
                    added = SimpleErrorReport(Strings.Errors.invalidConditionNoBoolean()),
                )
            }
        }
    }

    override val requiresInstance: Boolean
        get() {
            path  // Ensure path is parsed
            return _nonStatic
        }

    override fun validate(validator: Validator) {
        path  // Ensure path is parsed
        validationReport?.invoke(validator)
    }

    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        appendRichString {
            color = TextColor.BrightYellow
            append(type)
        }
        append(".$pathSource")
    }

    class Invalid(
        private val type: Type,
        private val invalidExpression: String,
    ) : VariableBaseImpl() {

        override val pathSource: String
            get() = invalidExpression

        override val root: NodeModel
            get() = NodeModelImpl(type)

        override val path: List<Member>
            get() = emptyList()

        override val requiresInstance: Boolean
            get() = false

        override fun validate(validator: Validator) {
            validator.reportError(Strings.Errors.invalidCondition(expression = invalidExpression))
        }

        override fun toString(childContext: MayBeInvalid?) = buildRichString {
            color = TextColor.Red
            append("<invalid-condition>")
        }
    }

    companion object Factory : BiObjectCache<Type, String, ConditionModelImpl>() {
        operator fun invoke(type: Type, pathSource: String): VariableBaseImpl {
            if (!pathSource.matches(ConditionRegex)) {
                return Invalid(type, pathSource)
            }
            return createCached(type, pathSource) { ConditionModelImpl(type, pathSource) }
        }

        private fun findAccessor(type: TypeDeclaration, name: String): Member? {
            val field = type.fields.find { it.name == name }
            if (field != null) {
                return field
            }

            val method = type.methods.find { method ->
                method.name == name
            }
            if (method != null) {
                return method
            }
            return null
        }

        private val ConditionRegex = "^(?:[A-Za-z_][A-Za-z0-9_]*\\.)*[A-Za-z_][A-Za-z0-9_]*\$".toRegex()
    }
}