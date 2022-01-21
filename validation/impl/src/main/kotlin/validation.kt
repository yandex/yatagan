package com.yandex.daggerlite.validation.impl

import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.ValidationMessage
import com.yandex.daggerlite.validation.Validator
import kotlin.LazyThreadSafetyMode.NONE

interface LocatedMessage {
    val message: ValidationMessage
    val encounterPaths: Collection<List<Any>>
}

private class ValidatorImpl : Validator {
    private val _children = arrayListOf<MayBeInvalid>()
    val children: List<MayBeInvalid>
        get() = _children
    private val _messages = lazy(NONE) { arrayListOf<ValidationMessage>() }
    val messages: List<ValidationMessage>
        get() = if (_messages.isInitialized()) _messages.value else emptyList()

    override fun report(message: ValidationMessage) {
        _messages.value += message
    }

    override fun child(node: MayBeInvalid) {
        _children += node
    }

    override fun inline(node: MayBeInvalid) {
        node.validate(this)
    }
}

fun validate(
    roots: Iterable<MayBeInvalid>,
): Collection<LocatedMessage> {
    val cache = hashMapOf<MayBeInvalid, ValidatorImpl>()

    val result: MutableMap<ValidationMessage, MutableSet<List<MayBeInvalid>>> = mutableMapOf()

    val markedGray = hashSetOf<MayBeInvalid>()
    val markedBlack = hashSetOf<MayBeInvalid>()
    val stack = arrayListOf<MutableList<MayBeInvalid>>()

    stack.add(roots.toMutableList())

    while (stack.isNotEmpty()) {
        // Substack is introduced to preserve node hierarchy
        val subStack = stack.last()
        if (subStack.isEmpty()) {
            stack.removeLast()
            continue
        }
        when (val node = subStack.last()) {
            in markedBlack -> {
                // TODO: Report already encountered errors if any.
                subStack.removeLast()
            }
            in markedGray -> {
                subStack.removeLast()
                markedBlack += node
                markedGray -= node
            }
            else -> {
                markedGray += node
                val validator = cache.getOrPut(node) {
                    ValidatorImpl().apply(node::validate)
                }
                for (message in validator.messages) {
                    // Extract current path from stack::substack
                    result.getOrPut(message, ::mutableSetOf) += stack.map { it.last() }
                }
                stack += validator.children.filterTo(mutableListOf()) { it !in markedGray }
            }
        }
    }

    return result.map { (message, paths) ->
        object : LocatedMessage {
            override val message = message
            override val encounterPaths = paths.reversed()  // reverse leads to more natural iteration order.
        }
    }
}