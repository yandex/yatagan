package com.yandex.daggerlite.spi

import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * A Service Interface for a dagger-lite plugin for graph visitation/validation.
 */
interface ValidationPlugin : MayBeInvalid {

    /**
     * Name of a validation plugin that will be used for message reporting.
     */
    override fun toString(): String
}