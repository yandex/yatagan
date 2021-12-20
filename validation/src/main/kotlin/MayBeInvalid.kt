package com.yandex.daggerlite.validation

/**
 * An interface for the models, which are eligible for validation (which may be invalid).
 *
 * Such models should be implemented with the following in mind:
 * - Model implementations should internally support two states:
 */
interface MayBeInvalid {
    /**
     * Yields validation messages associated with this particular model.
     * If no validation messages are present, should return empty collection.
     * TODO: fix doc.
     */
    fun validate(validator: Validator)
}