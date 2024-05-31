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

import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.isKotlinObject
import com.yandex.yatagan.lang.scope.FactoryKey
import com.yandex.yatagan.lang.scope.LexicalScope
import com.yandex.yatagan.lang.scope.caching
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendRichString
import com.yandex.yatagan.validation.format.buildRichString
import com.yandex.yatagan.validation.format.reportError
import com.yandex.yatagan.validation.format.reportWarning

private object MemberTypeVisitor : Member.Visitor<Type> {
    override fun visitOther(model: Member) = throw AssertionError()
    override fun visitMethod(model: Method) = model.returnType
    override fun visitField(model: Field) = model.type
}

internal class ConditionModelImpl private constructor(
    data: Pair<Type, String>,
) : VariableBaseImpl(), LexicalScope by data.first {
    private val type = data.first
    override val pathSource: String = data.second

    override val root: NodeModel
        get() = NodeModelImpl(
            type = type,
            qualifier = null,
        )

    override val path: List<Member> by lazy {
        buildList {
            pathSource.splitToSequence('.').foldIndexed(type) { index, currentType, name ->
                val resolvedAccessor = findAccessor(
                    type = currentType.declaration,
                    name = name,
                    allowStatic = index == 0,
                ) ?: return@buildList
                add(resolvedAccessor)
                resolvedAccessor.accept(MemberTypeVisitor)
            }
        }
    }

    override val requiresInstance: Boolean
        get() = path.firstOrNull()?.isStatic == false

    override fun validate(validator: Validator) {
        pathSource.split('.').let { memberNames ->
            if (memberNames.size > path.size) {
                validator.reportError(Strings.Errors.invalidConditionMissingMember(
                    name = memberNames[path.size],
                    type = path.lastOrNull()?.accept(MemberTypeVisitor) ?: type,
                ))
            }
        }
        if (path.isNotEmpty()) {
            if (path.last().accept(MemberTypeVisitor).asBoxed().declaration.qualifiedName != Names.Boolean) {
                validator.reportError(Strings.Errors.invalidConditionNoBoolean())
            }
            if (!path.first().isStatic && type.declaration.isKotlinObject) {
                validator.reportWarning(Strings.Warnings.nonStaticConditionOnKotlinObject())
            }
            path.forEach { member ->
                if (!member.owner.isEffectivelyPublic) {
                    validator.reportError(Strings.Errors.invalidAccessForConditionClass(`class` = member.owner))
                }
                if (!member.isEffectivelyPublic) {
                    validator.reportError(Strings.Errors.invalidAccessForConditionMember(member = member))
                }
            }
        }
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
    ) : VariableBaseImpl(), LexicalScope by type {

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

    companion object Factory : FactoryKey<Pair<Type, String>, VariableBaseImpl> {
        private object Caching : FactoryKey<Pair<Type, String>, ConditionModelImpl> {
            override fun LexicalScope.factory() = caching(::ConditionModelImpl)
        }

        override fun LexicalScope.factory() = fun LexicalScope.(data: Pair<Type, String>): VariableBaseImpl {
            val (type, pathSource) = data
            return if (!pathSource.matches(ConditionRegex)) {
                Invalid(type, pathSource)
            } else Caching(data)
        }

        private fun findAccessor(type: TypeDeclaration, name: String, allowStatic: Boolean): Member? {
            return (type.fields.find { it.name == name && (allowStatic || !it.isStatic) }
                ?: type.methods.find { it.name == name && it.parameters.none() && (allowStatic || !it.isStatic) })
        }

        private val ConditionRegex = "^(?:[A-Za-z_][A-Za-z0-9_]*\\.)*[A-Za-z_][A-Za-z0-9_]*\$".toRegex()
    }
}