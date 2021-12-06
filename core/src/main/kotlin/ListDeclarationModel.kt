package com.yandex.daggerlite.core

/**
 * [com.yandex.daggerlite.DeclareList] model
 */
interface ListDeclarationModel {
    /**
     * An element's type for a multi-bound list.
     */
    val listType: NodeModel

    /**
     * Whether to sort elements according topologically according to their dependencies on each other.
     */
    val orderByDependency: Boolean
}