package com.yandex.yatagan.core.graph

import com.yandex.yatagan.core.graph.bindings.AlternativesBinding
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyBinding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyEntryPointBinding
import com.yandex.yatagan.core.graph.bindings.ComponentInstanceBinding
import com.yandex.yatagan.core.graph.bindings.EmptyBinding
import com.yandex.yatagan.core.graph.bindings.InstanceBinding
import com.yandex.yatagan.core.graph.bindings.MapBinding
import com.yandex.yatagan.core.graph.bindings.MultiBinding
import com.yandex.yatagan.core.graph.bindings.ProvisionBinding
import com.yandex.yatagan.core.graph.bindings.SubComponentFactoryBinding
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.lang.Method

/**
 * Discards negation from the literal.
 *
 * @return `!this` if negated, `this` otherwise.
 */
public fun ConditionModel.normalized(): ConditionModel {
    return if (negated) !this else this
}

public operator fun GraphEntryPoint.component1(): Method = getter

public operator fun GraphEntryPoint.component2(): NodeDependency = dependency

public abstract class BindingVisitorAdapter<R> : Binding.Visitor<R> {
    public abstract fun visitDefault(): R
    override fun visitProvision(binding: ProvisionBinding): R = visitDefault()
    override fun visitAssistedInjectFactory(binding: AssistedInjectFactoryBinding): R = visitDefault()
    override fun visitInstance(binding: InstanceBinding): R = visitDefault()
    override fun visitAlternatives(binding: AlternativesBinding): R = visitDefault()
    override fun visitSubComponentFactory(binding: SubComponentFactoryBinding): R = visitDefault()
    override fun visitComponentDependency(binding: ComponentDependencyBinding): R = visitDefault()
    override fun visitComponentInstance(binding: ComponentInstanceBinding): R = visitDefault()
    override fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding): R = visitDefault()
    override fun visitMulti(binding: MultiBinding): R = visitDefault()
    override fun visitMap(binding: MapBinding): R = visitDefault()
    override fun visitEmpty(binding: EmptyBinding): R = visitDefault()
}

public fun <P : WithParents<P>> P.parentsSequence(
    includeThis: Boolean = false,
): Sequence<P> {
    return object : Sequence<P> {
        val initial = if (includeThis) this@parentsSequence else parent
        override fun iterator() = object : Iterator<P> {
            var next: P? = initial
            override fun hasNext() = next != null
            override fun next() = (next ?: throw NoSuchElementException()).also { next = it.parent }
        }
    }
}

public fun <C : WithChildren<C>> C.childrenSequence(
    includeThis: Boolean = true,
): Sequence<C> {
    return object : Sequence<C> {
        val initial: Collection<C> = if (includeThis) listOf(this@childrenSequence) else children

        override fun iterator() = object : Iterator<C> {
            val queue = ArrayDeque(initial)

            override fun hasNext(): Boolean = queue.isNotEmpty()

            override fun next(): C {
                val next = queue.removeFirst()
                queue.addAll(next.children)
                return next
            }
        }
    }
}