package com.yandex.yatagan.instrumentation.impl

import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.impl.NodeModel
import com.yandex.yatagan.instrumentation.Expression
import com.yandex.yatagan.instrumentation.Statement

fun InstrumentedAround.isEmpty(): Boolean = before.isEmpty() && after.isEmpty()

fun InstrumentedAfter.isEmpty(): Boolean = after.isEmpty()

var BindingGraph.instrumentedCreation: InstrumentedAfter
    get() = getOrNull(InstrumentedAfterKey) ?: NotInstrumented
    internal set(value) { this[InstrumentedAfterKey] = value }

var Binding.instrumentedAccess: InstrumentedAround
    get() = getOrNull(InstrumentedAroundAccessKey) ?: NotInstrumented
    internal set(value) { this[InstrumentedAroundAccessKey] = value }

var Binding.instrumentedCreation: InstrumentedAround
    get() = getOrNull(InstrumentedAroundCreationKey) ?: NotInstrumented
    internal set(value) { this[InstrumentedAroundCreationKey] = value }

fun Binding.instrumentedDependencies(): Set<NodeDependency> {
    if (instrumentedAccess.isEmpty() && instrumentedCreation.isEmpty())
        return emptySet()

    return buildSet {
        instrumentedAccess.collectNewDependencies(this)
        instrumentedCreation.collectNewDependencies(this)
    }
}

fun Expression.ResolveInstance.asNode(): NodeModel = NodeModel(
    type = type,
    qualifier = qualifier,
)

internal fun InstrumentedAfter.collectNewDependencies(into: MutableSet<in NodeModel>) {
    with(InstrumentedDependenciesExtractor(into)) {
        after.forEach { it.accept(this) }
    }
}

internal fun InstrumentedAround.collectNewDependencies(into: MutableSet<in NodeModel>) {
    with(InstrumentedDependenciesExtractor(into)) {
        before.forEach { it.accept(this) }
        after.forEach { it.accept(this) }
    }
}

private val InstrumentedAroundCreationKey = declareKeyFor<InstrumentedAround>()
private val InstrumentedAroundAccessKey = declareKeyFor<InstrumentedAround>()
private val InstrumentedAfterKey = declareKeyFor<InstrumentedAfter>()

private object NotInstrumented : InstrumentedAround {
    override val before: List<Statement> get() = emptyList()
    override val after: List<Statement> get() = emptyList()
}

private inline fun <reified T : Any> declareKeyFor(): Extensible.Key<T> = object : Extensible.Key<T> {
    override val keyType: Class<T> get() = T::class.java
}
