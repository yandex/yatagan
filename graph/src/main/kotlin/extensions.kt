package com.yandex.daggerlite.graph

/**
 * Discards negation from the literal.
 *
 * @return `!this` if negated, `this` otherwise.
 */
fun ConditionScope.Literal.normalized(): ConditionScope.Literal {
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