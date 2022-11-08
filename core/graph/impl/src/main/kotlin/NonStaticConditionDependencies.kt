package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.bindings.BaseBinding
import com.yandex.yatagan.core.graph.impl.bindings.ConditionalBindingMixin
import com.yandex.yatagan.core.graph.impl.bindings.graphConditionScope
import com.yandex.yatagan.core.graph.impl.bindings.resolveAliasChain
import com.yandex.yatagan.core.model.ConditionExpression
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

/**
 * See [com.yandex.yatagan.core.graph.bindings.Binding.nonStaticConditionProviders].
 */
internal interface NonStaticConditionDependencies : MayBeInvalid {
    val conditionProviders: Set<NodeModel>
}

/**
 * Constructor function for [NonStaticConditionDependencies].
 */
internal fun NonStaticConditionDependencies(binding: ConditionalBindingMixin): NonStaticConditionDependencies {
    return when (binding.variantMatch.conditionScope) {
        ConditionExpression.Unscoped, ConditionExpression.NeverScoped -> EmptyNonStaticConditionDependencies
        else -> {
            val conditionProviders = buildSet {
                for (clause in binding.variantMatch.conditionScope.expression) for (condition in clause) {
                    if (condition.requiresInstance)
                        add(condition.root)
                }
            }
            if (conditionProviders.isEmpty()) {
                EmptyNonStaticConditionDependencies
            } else {
                NonStaticConditionDependenciesImpl(
                    conditionProviders = conditionProviders,
                    host = binding,
                )
            }
        }
    }
}

private object EmptyNonStaticConditionDependencies : NonStaticConditionDependencies {
    override val conditionProviders get() = emptySet<Nothing>()
    override fun validate(validator: Validator) = Unit
    override fun toString(childContext: MayBeInvalid?) = ""
}

private class NonStaticConditionDependenciesImpl(
    override val conditionProviders: Set<NodeModel>,
    private val host: ConditionalBindingMixin,
) : NonStaticConditionDependencies {

    override fun validate(validator: Validator) {
        for (conditionProvider in conditionProviders) {
            validator.child(host.owner.resolveBindingRaw(conditionProvider))
        }

        val conditionScope = host.graphConditionScope()
        for (node in conditionProviders) {
            val resolved = host.owner.resolveBinding(node)
            val resolvedScope = resolved.graphConditionScope()
            if (resolvedScope !in conditionScope) {
                // Incompatible condition!
                validator.reportError(Strings.Errors.incompatibleConditionForConditionProvider(
                    aCondition = resolvedScope,
                    bCondition = conditionScope,
                    a = resolved,
                    b = host,
                )) {
                    val aliases = host.owner.resolveAliasChain(node)
                    if (aliases.isNotEmpty()) {
                        addNote(Strings.Notes.conditionPassedThroughAliasChain(aliases = aliases))
                    }
                }
            }
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "its non-static conditions",
        representation = {
            color = TextColor.Gray
            if (childContext is BaseBinding) {
                append("{ ")
                var count = 0
                for (clause in host.variantMatch.conditionScope.expression) for (literal in clause) {
                    if (literal.requiresInstance) {
                        ++count
                        if (literal.root == childContext.target) {
                            appendChildContextReference(literal)
                        }
                    }
                }
                if (count > 1) {
                    append(", ..")
                }
                append(" }")
            } else {
                append(" { .. }")
            }
        }
    )
}