package com.yandex.dagger3.core

/**
 * Represents a way to provide a [NodeModel].
 * Each [NodeModel] must have a single [Binding] for a [BindingGraph] to be valid.
 */
sealed interface Binding {
    /**
     * A node that this binding provides.
     */
    val target: NodeModel

    /**
     * Optional binding scope.
     * If present then the binding is called "scoped" binding - it must cache provided instance
     * inside a [ComponentModel] of the same scope.
     */
    val scope: Scope?

    /**
     * Represent provision cache scope.
     * Must provide [equals]/[hashCode] implementation.
     */
    interface Scope
}