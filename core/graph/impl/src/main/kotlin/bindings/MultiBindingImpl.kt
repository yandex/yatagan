package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.base.MapComparator
import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.graph.bindings.MultiBinding
import com.yandex.daggerlite.core.graph.impl.topologicalSort
import com.yandex.daggerlite.core.model.CollectionTargetKind
import com.yandex.daggerlite.core.model.ModuleHostedBindingModel
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendRichString
import com.yandex.daggerlite.validation.format.bindingModelRepresentation
import com.yandex.daggerlite.validation.format.buildRichString

internal class MultiBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
    override val upstream: MultiBindingImpl?,
    override val targetForDownstream: NodeModel,
    override val kind: CollectionTargetKind,
    contributions: Map<Contribution, MultiBinding.ContributionType>,
) : MultiBinding, BindingDefaultsMixin, ComparableBindingMixin<MultiBindingImpl> {
    private val _contributions = contributions

    data class Contribution(
        val contributionDependency: NodeModel,
        val origin: ModuleHostedBindingModel,
    ) : Comparable<Contribution> {
        override fun compareTo(other: Contribution): Int {
            return origin.method.compareTo(other.origin.method)
        }
    }

    override val contributions: Map<NodeModel, MultiBinding.ContributionType> by lazy {
        when (kind) {
            CollectionTargetKind.List -> {
                // Resolve aliases as multi-bindings often work with @Binds
                val resolved = _contributions.mapKeys { (contribution, _) ->
                    owner.resolveBinding(contribution.contributionDependency).target
                }
                topologicalSort(
                    nodes = resolved.keys,
                    inside = owner,
                ).associateWith(resolved::getValue)
            }

            CollectionTargetKind.Set -> {
                _contributions.mapKeys { it.key.contributionDependency }
            }
        }
    }

    override val dependencies get() = extensibleAwareDependencies(
        _contributions.keys.asSequence().map { it.contributionDependency })

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = when(kind) {
            CollectionTargetKind.List -> "list-binding"
            CollectionTargetKind.Set -> "set-binding"
        },
        childContext = childContext,
        representation = { append("List ") },
        childContextTransform = { dependency ->
            when (dependency.node) {
                upstream?.targetForDownstream -> "<inherited from parent component>"
                else -> dependency
            }
        },
        ellipsisStatistics = {_, dependencies ->
            var elements = 0
            var collections = 0
            var mentionUpstream = false
            for (dependency in dependencies) when(contributions[dependency.node]) {
                MultiBinding.ContributionType.Element -> elements++
                MultiBinding.ContributionType.Collection -> collections++
                null -> mentionUpstream = (dependency.node == upstream?.targetForDownstream)
            }
            sequenceOf(
                when(elements) {
                    0 -> null
                    1 -> "1 element"
                    else -> "$elements elements"
                },
                when(collections) {
                    0 -> null
                    1 -> "1 collection"
                    else -> "$collections collections"
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

    override fun compareTo(other: MultiBindingImpl): Int {
        return MapComparator.ofComparable<Contribution, MultiBinding.ContributionType>()
            .compare(_contributions, other._contributions)
    }

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitMulti(this)
    }
}