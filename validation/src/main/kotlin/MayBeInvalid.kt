package com.yandex.daggerlite.validation

/**
 * An interface for the models, which are eligible for validation (which may be invalid).
 *
 * Models, implementing the interface, are nodes of a validation graph.
 *
 * Model implementations should internally support two states:
 *   - valid state where all contracts of the API are met.
 *   In valid state a node makes no calls to [Validator.report] with an [Error][ValidationMessage.Kind.Error] kind.
 *
 *   - invalid state where contracts of the API are met on the best-effort basis with a focus on minimizing
 *   induced (ghost) errors.
 *   In invalid state a node makes one or more calls to [Validator.report] with an
 *   [Error][ValidationMessage.Kind.Error] kind.
 *
 * - Regardless of the validity state a node may call [Validator.inline]/[Validator.child] to build a validation graph.
 *
 * @see Validator
 */
interface MayBeInvalid {
    /**
     * `accept`-like method with a [Validator] as visitor.
     * Implementations should report their validity state and build validation graph.
     *
     * NOTE: This method should only make decisions based on internal model state, without taking environment into
     * account ("Purity" requirement).
     */
    fun validate(validator: Validator)
}