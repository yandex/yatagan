package com.yandex.yatagan.core.model

/**
 * Formally denotes graph edges' type.
 * Informally a [NodeModel] can depend on other [NodeModel]s with this kind of dependency.
 *
 * @see NodeDependency
 */
enum class DependencyKind {
    /**
     * Type is requested directly (eagerly).
     */
    Direct,

    /**
     * [com.yandex.yatagan.Lazy]-wrapped type is requested.
     */
    Lazy,

    /**
     * [javax.inject.Provider]-wrapped type is requested.
     */
    Provider,

    /**
     * [com.yandex.yatagan.Optional]-wrapped type is requested.
     */
    Optional,

    /**
     * `Optional<Lazy<...>>`-wrapped type is requested.
     *
     * @see com.yandex.yatagan.Optional
     * @see com.yandex.yatagan.Lazy
     */
    OptionalLazy,

    /**
     * `Optional<Provider<...>>`-wrapped type is requested.
     *
     * @see com.yandex.yatagan.Optional
     * @see javax.inject.Provider
     */
    OptionalProvider,
}