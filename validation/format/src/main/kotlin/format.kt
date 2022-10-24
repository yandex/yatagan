@file:[JvmMultifileClass JvmName("Format") ]
package com.yandex.daggerlite.validation.format

import com.yandex.daggerlite.core.graph.bindings.AliasBinding
import com.yandex.daggerlite.core.graph.bindings.BaseBinding
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.model.ConditionExpression
import com.yandex.daggerlite.core.model.ConditionScope
import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.RichString

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
        override fun visitAlias(alias: AliasBinding) = listOf(alias.source)
        override fun visitBinding(binding: Binding) = binding.dependencies.toList()
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
    return when(childContext) {
        is BaseBinding -> accept(object : BaseBinding.Visitor<NodeDependency?> {
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
    when(any) {
        is MayBeInvalid -> append(any.toString(childContext = null))
        is CharSequence -> append(any)
        is ConditionExpression<*> -> append(any.toString(childContext = null))
        null -> append(Unresolved)
        else -> append(any.toString())
    }
}

fun ConditionExpression<*>.toString(childContext: MayBeInvalid?): RichString = buildRichString {
    when (this@toString) {
        ConditionScope.Unscoped -> {
            color = TextColor.Green
            append(OpenBracket)
            append("always-present")
            append(CloseBracket)
        }

        ConditionScope.NeverScoped -> {
            color = TextColor.Magenta
            append(OpenBracket)
            append("never-present")
            append(CloseBracket)
        }

        else -> {
            fun RichStringBuilder.appendLiteral(literal: ConditionExpression.Literal) {
                if (literal == childContext) {
                    appendChildContextReference(reference = literal)
                } else {
                    append(literal)
                }
            }

            expression.joinTo(
                buffer = this,
                prefix = OpenBracket,
                separator = LogicalAnd,
                postfix = CloseBracket,
            ) { clause ->
                if (clause.size == 1) {
                    buildRichString {
                        appendLiteral(clause.first())
                    }
                } else {
                    buildRichString {
                        clause.joinTo(
                            buffer = this,
                            prefix = OpenParenthesis,
                            separator = LogicalOr,
                            postfix = CloseParenthesis,
                            transform = {
                                buildRichString {
                                    appendLiteral(it)
                                }
                            },
                        )
                    }
                }
            }
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