package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.base.ListComparator
import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.BaseBinding
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.graph.bindings.MapBinding
import com.yandex.daggerlite.core.graph.impl.mergeMultiMapsForDuplicateCheck
import com.yandex.daggerlite.core.model.ModuleHostedBindingModel
import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.lang.Annotation
import com.yandex.daggerlite.lang.Type
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendRichString
import com.yandex.daggerlite.validation.format.bindingModelRepresentation
import com.yandex.daggerlite.validation.format.buildRichString
import com.yandex.daggerlite.validation.format.reportError

internal class MapBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
    override val contents: List<Contribution>,
    override val mapKey: Type,
    override val mapValue: Type,
    override val upstream: MapBindingImpl?,
    override val targetForDownstream: NodeModel,
) : MapBinding, BindingDefaultsMixin, ComparableBindingMixin<MapBindingImpl> {

    data class Contribution(
        override val keyValue: Annotation.Value,
        override val dependency: NodeDependency,
        val origin: ModuleHostedBindingModel,
    ) : MapBinding.Contribution, Comparable<Contribution> {
        override fun compareTo(other: Contribution): Int {
            return origin.method.compareTo(other.origin.method)
        }
    }

    private val allResolvedAndGroupedContents: Map<Annotation.Value, List<BaseBinding>> by lazy {
        mergeMultiMapsForDuplicateCheck(
            fromParent = upstream?.allResolvedAndGroupedContents,
            current = contents.groupBy(
                keySelector = { (key, _) -> key },
                // Resolution on `owner` is important here, so do it eagerly
                valueTransform = { it -> owner.resolveBindingRaw(it.dependency.node) },
            ),
        )
    }

    override val dependencies: Sequence<NodeDependency>
        get() = extensibleAwareDependencies(contents.asSequence().map { (_, dependency) -> dependency })

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitMap(this)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)

        for ((key, bindings) in allResolvedAndGroupedContents) {
            if (bindings.size > 1) {
                // Found duplicates
                validator.reportError(Strings.Errors.duplicateKeysInMapping(mapType = target, keyValue = key)) {
                    for (binding in bindings) {
                        addNote(Strings.Notes.duplicateKeyInMapBinding(binding = binding))
                    }
                }
            }
        }
    }

    override fun compareTo(other: MapBindingImpl): Int {
        return ListComparator.ofComparable<Contribution>(asSorted = true)
            .compare(contents, other.contents)
    }

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "map-binding of",
        childContext = childContext,
        representation = {
            append(mapKey)
            appendRichString {
                color = TextColor.Gray
                append(" to ")
            }
            append(mapValue)
        },
        childContextTransform = { context ->
            val key = contents.find { (_, dependency) -> dependency == context }?.keyValue
            if (key != null) {
                "$key -> $mapValue"
            } else if (upstream?.targetForDownstream == context) {
                "<inherited from parent component>"
            } else throw AssertionError()
        },
        ellipsisStatistics = {_,  dependencies ->
            var elements = 0
            var mentionUpstream = false
            for (dependency in dependencies) when(dependency) {
                upstream?.targetForDownstream -> mentionUpstream = true
                else -> elements++
            }
            sequenceOf(
                when(elements) {
                    0 -> null
                    1 -> "1 element"
                    else -> "$elements elements"
                },
                if (mentionUpstream) "upstream" else null,
            ).filterNotNull().joinTo(this, separator = " + ")
        },
        openBracket = " { ",
        closingBracket = buildRichString {
            append(" } ")
            appendRichString {
                color = TextColor.Gray
                append("assembled in ")
            }
            append(owner)
        },
    )
}