package com.yandex.daggerlite.core.graph

import com.yandex.daggerlite.core.model.ConditionModel

/**
 * Discards negation from the literal.
 *
 * @return `!this` if negated, `this` otherwise.
 */
fun ConditionModel.normalized(): ConditionModel {
    return if (negated) !this else this
}

operator fun GraphEntryPoint.component1() = getter

operator fun GraphEntryPoint.component2() = dependency

abstract class BindingVisitorAdapter<R> : Binding.Visitor<R> {
    abstract fun visitDefault(): R
    override fun visitProvision(binding: ProvisionBinding) = visitDefault()
    override fun visitAssistedInjectFactory(binding: AssistedInjectFactoryBinding) = visitDefault()
    override fun visitInstance(binding: InstanceBinding) = visitDefault()
    override fun visitAlternatives(binding: AlternativesBinding) = visitDefault()
    override fun visitSubComponentFactory(binding: SubComponentFactoryBinding) = visitDefault()
    override fun visitComponentDependency(binding: ComponentDependencyBinding) = visitDefault()
    override fun visitComponentInstance(binding: ComponentInstanceBinding) = visitDefault()
    override fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding) = visitDefault()
    override fun visitMulti(binding: MultiBinding) = visitDefault()
    override fun visitMap(binding: MapBinding) = visitDefault()
    override fun visitEmpty(binding: EmptyBinding) = visitDefault()
}

fun <P : WithParents<P>> P.parentsSequence(
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

fun <C : WithChildren<C>> C.childrenSequence(
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