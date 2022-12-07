/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.validation.impl

import com.yandex.yatagan.base.traverseDepthFirstWithPath
import com.yandex.yatagan.base.zipWithNextOrNull
import com.yandex.yatagan.validation.LocatedMessage
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.ValidationMessage
import com.yandex.yatagan.validation.Validator
import kotlin.LazyThreadSafetyMode.NONE

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
    root: MayBeInvalid,
): Collection<LocatedMessage> {
    val cache = hashMapOf<MayBeInvalid, ValidatorImpl>()
    val result: MutableMap<ValidationMessage, MutableSet<List<MayBeInvalid>>> = mutableMapOf()

    traverseDepthFirstWithPath(
        roots = listOf(root),
        childrenOf = { cache[it]?.children ?: emptyList() },
        visit = { path, node ->
            val validator = cache.getOrPut(node) {
                ValidatorImpl().apply(node::validate)
            }
            for (message in validator.messages) {
                // Extract current path from stack::substack
                result.getOrPut(message, ::mutableSetOf) += path.toList()
            }
        }
    )

    return result.map { (message, paths) ->
        val pathStrings: MutableList<List<CharSequence>> = paths.mapTo(arrayListOf()) { path: List<MayBeInvalid> ->
            path.zipWithNextOrNull { node: MayBeInvalid, itsChild: MayBeInvalid? ->
                node.toString(childContext = itsChild)
            }
        }
        pathStrings.sortWith(PathComparator)

        LocatedMessage(
            message = object : ValidationMessage by message {
                override val notes: Collection<CharSequence> = message.notes.sortedWith(CharSequenceComparator)
            },
            encounterPaths = pathStrings,
        )
    }
}
