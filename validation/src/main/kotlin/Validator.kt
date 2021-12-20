package com.yandex.daggerlite.validation

import com.yandex.daggerlite.validation.Validator.ChildValidationKind.Nested

interface Validator {
    enum class ChildValidationKind {
        Nested,
        Inline,
    }

    fun report(message: ValidationMessage)

    // TODO: make Collection<MayBeInvalid>/Sequence<MayBeInvalid> overloads
    fun child(node: MayBeInvalid, kind: ChildValidationKind = Nested)
}