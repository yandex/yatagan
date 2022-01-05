package com.yandex.daggerlite.validation.impl

import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.ValidationMessage
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.Validator.ChildValidationKind.Inline
import com.yandex.daggerlite.validation.Validator.ChildValidationKind.Nested

interface LocatedMessage {
    val message: ValidationMessage
    val encounterPaths: Collection<List<Any>>
}

private class ValidatorImpl : Validator {
    val children = arrayListOf<MayBeInvalid>()
    val messages = arrayListOf<ValidationMessage>()

    override fun report(message: ValidationMessage) {
        messages += message
    }

    override fun child(node: MayBeInvalid, kind: Validator.ChildValidationKind) {
        when (kind) {
            Nested -> children += node
            Inline -> node.validate(this)
        }
    }
}

fun validate(
    roots: Iterable<MayBeInvalid>,
): Collection<LocatedMessage> {
    val validationCache = hashMapOf<MayBeInvalid, List<ValidationMessage>>()
    val result: MutableMap<ValidationMessage, MutableSet<List<MayBeInvalid>>> = mutableMapOf()
    val currentPath = arrayListOf<MayBeInvalid>()

    // TODO: write non-recursive algorithm
    fun validateImpl(node: MayBeInvalid) {
        currentPath.add(node)
        try {
            val messages: List<ValidationMessage>
            val children: Collection<MayBeInvalid>
            if (node in validationCache) {
                messages = validationCache[node]!!
                children = emptyList()
            } else {
                val validator = ValidatorImpl()
                node.validate(validator)
                messages = validator.messages
                children = validator.children
                // fixme: children error messages are not always being referenced fully, some usages may be dropped
                //  due to `validationCache`: child can't be reached more than once through one parent. This is not a
                //  very important error, as an error will still be reported, only its paths of occurrence may hide
                //  each other.
                validationCache[node] = messages
            }

            for (message in messages) {
                result.getOrPut(message, ::mutableSetOf) += ArrayList(currentPath)
            }
            for (child in children) {
                validateImpl(child)
            }
        } finally {
            assert(currentPath.removeLast() == node)
        }
    }

    for (root in roots) {
        validateImpl(root)
    }

    return result.map {  (message, paths) ->
        object : LocatedMessage {
            override val message get() = message
            override val encounterPaths get() = paths
        }
    }
}