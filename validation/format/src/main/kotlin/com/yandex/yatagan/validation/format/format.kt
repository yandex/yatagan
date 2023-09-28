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

@file:[JvmMultifileClass JvmName("Format")]

package com.yandex.yatagan.validation.format

import com.yandex.yatagan.core.graph.bindings.AliasBinding
import com.yandex.yatagan.core.graph.bindings.BaseBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.BooleanExpression
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.RichString

inline fun buildRichString(
    block: RichStringBuilder.() -> Unit,
): RichString = RichStringBuilder().apply(block).build()

inline fun RichStringBuilder.appendRichString(
    block: RichStringBuilder.() -> Unit,
): RichStringBuilder = append(buildRichString(block))

fun modelRepresentation(
    modelClassName: String,
    representation: Any,
) = buildRichString {
    appendRichString {
        color = TextColor.Gray
        append("$modelClassName ")
    }
    append(representation)
}

inline fun modelRepresentation(
    modelClassName: String,
    representation: RichStringBuilder.() -> Unit,
) = buildRichString {
    appendRichString {
        color = TextColor.Gray
        append("$modelClassName ")
    }
    append(buildRichString(representation))
}

inline fun BaseBinding.bindingModelRepresentation(
    modelClassName: String,
    childContext: MayBeInvalid?,
    representation: RichStringBuilder.() -> Unit,
    childContextTransform: (NodeDependency) -> Any = { it },
    ellipsisStatistics: RichStringBuilder.(
        context: NodeDependency?,
        dependencies: List<NodeDependency>,
    ) -> Unit = { context, dependencies ->
        when (val count = dependencies.size) {
            in Int.MIN_VALUE..0 -> Unit // Append nothing
            1 -> {
                if (context == null) append(".. ") else append("+ ")
                append("1 dependency")
            }
            else -> {
                if (context == null) append(".. ") else append("+ ")
                append(count).append(" dependencies")
            }
        }
    },
    openBracket: CharSequence = "(",
    closingBracket: CharSequence = ")",
) = modelRepresentation(
    modelClassName = modelClassName,
) {
    append(buildRichString(representation))
    val context = findChildContextNode(childContext)

    val dependencies = accept(object : BaseBinding.Visitor<List<NodeDependency>> {
        override fun visitOther(other: BaseBinding) = throw AssertionError()
        override fun visitAlias(alias: AliasBinding) = listOf(alias.source)
        override fun visitBinding(binding: Binding) = binding.dependencies
    })

    append(openBracket)
    if (context != null) {
        appendChildContextReference(childContextTransform(context))
        if (dependencies.size > 1) {
            append(", ")
        }
        val withoutContext = dependencies - context
        ellipsisStatistics(context, withoutContext)
    } else {
        ellipsisStatistics(null, dependencies)
    }
    append(closingBracket)
}

fun BaseBinding.findChildContextNode(childContext: MayBeInvalid?): NodeDependency? {
    return when (childContext) {
        is BaseBinding -> accept(object : BaseBinding.Visitor<NodeDependency?> {
            override fun visitOther(other: BaseBinding) = throw AssertionError()
            override fun visitAlias(alias: AliasBinding) = alias.source.takeIf { it == childContext.target }
            override fun visitBinding(binding: Binding) = binding.dependencies.find { it.node == childContext.target }
        })
        else -> null
    }
}

fun RichStringBuilder.appendChildContextReference(
    reference: Any,
): RichStringBuilder = apply {
    appendRichString {
        color = TextColor.BrightYellow
        isBold = true
        isChildContext = true
        append(reference)
    }
}

fun RichStringBuilder.append(any: Any?): RichStringBuilder = apply {
    when (any) {
        is MayBeInvalid -> append(any.toString(childContext = null))
        is CharSequence -> append(any)
        is ConditionScope -> append(any.toString(childContext = null))
        null -> append(Unresolved)
        else -> append(any.toString())
    }
}

fun RichStringBuilder.append(collection: Collection<Any?>): RichStringBuilder = apply {
    append('[')
    val lastIndex = collection.size - 1
    collection.forEachIndexed { index, any ->
        append(any)
        if (index != lastIndex) append(", ")
    }
    append(']')
}

fun ConditionScope.toString(childContext: MayBeInvalid?): RichString = buildRichString {
    when (this@toString) {
        ConditionScope.Always -> {
            color = TextColor.Green
            append(OpenBracket)
            append("always-present")
            append(CloseBracket)
        }

        ConditionScope.Never -> {
            color = TextColor.Magenta
            append(OpenBracket)
            append("never-present")
            append(CloseBracket)
        }

        is ConditionScope.ExpressionScope -> {
            append(OpenBracket)
            expression.accept(object : BooleanExpression.Visitor<Unit> {
                override fun visitVariable(variable: BooleanExpression.Variable) {
                    val model = variable.model
                    if (model == childContext) {
                        appendChildContextReference(reference = model)
                    } else {
                        append(model)
                    }
                }

                override fun visitNot(not: BooleanExpression.Not) {
                    append(Negation)
                    val shouldUseParentheses = not.underlying.accept(object : BooleanExpression.Visitor<Boolean> {
                        override fun visitVariable(variable: BooleanExpression.Variable) = false
                        override fun visitNot(not: BooleanExpression.Not) = false
                        override fun visitAnd(and: BooleanExpression.And) = true
                        override fun visitOr(or: BooleanExpression.Or) = true
                    })
                    if (shouldUseParentheses) {
                        append(OpenParenthesis)
                    }
                    not.underlying.accept(this)
                    if (shouldUseParentheses) {
                        append(CloseParenthesis)
                    }
                }

                override fun visitAnd(and: BooleanExpression.And) {
                    val parenthesesUsageDetector = object : BooleanExpression.Visitor<Boolean> {
                        override fun visitVariable(variable: BooleanExpression.Variable) = false
                        override fun visitNot(not: BooleanExpression.Not) = false
                        override fun visitAnd(and: BooleanExpression.And) = false
                        override fun visitOr(or: BooleanExpression.Or) = true
                    }

                    if (and.lhs.accept(parenthesesUsageDetector)) {
                        append(OpenParenthesis)
                        and.lhs.accept(this)
                        append(CloseParenthesis)
                    } else {
                        and.lhs.accept(this)
                    }

                    append(LogicalAnd)

                    if (and.rhs.accept(parenthesesUsageDetector)) {
                        append(OpenParenthesis)
                        and.rhs.accept(this)
                        append(CloseParenthesis)
                    } else {
                        and.rhs.accept(this)
                    }
                }

                override fun visitOr(or: BooleanExpression.Or) {
                    or.lhs.accept(this)
                    append(LogicalOr)
                    or.rhs.accept(this)
                }
            })
            append(CloseBracket)
        }
    }
}


inline fun <T> RichStringBuilder.appendMaybeMultiple(
    collection: Collection<T>,
    onEmpty: CharSequence = "",
    prefixOnSingle: CharSequence = "",
    prefixOnMultiple: CharSequence = "",
    postfixOnMultiple: CharSequence = "",
    separator: CharSequence = ", ",
    crossinline transform: (T) -> CharSequence,
): RichStringBuilder = apply {
    when (collection.size) {
        0 -> append(onEmpty)
        1 -> append(prefixOnSingle).append(transform(collection.single()))
        else -> collection.joinTo(this,
            prefix = prefixOnMultiple,
            postfix = postfixOnMultiple,
            separator = separator,
            transform = { transform(it) }
        )
    }
}