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

package com.yandex.yatagan.validation

/**
 * A visitor-like object that validates [MayBeInvalid].
 */
public interface Validator {
    /**
     * Reports given message for a [MayBeInvalid] node that is currently being visited.
     * Paths on which the message occurs will be grouped.
     */
    public fun report(message: ValidationMessage)

    /**
     * Adds a child node to a validation graph.
     *
     * @see inline
     */
    public fun child(node: MayBeInvalid)

    /**
     * Similar to [child], yet
     * performs validation of the [node] "inline": all the reported messages will be associated with a current node,
     * not with the given [node]. Moreover, [node]'s [toString] will never show on any path.
     *
     * Useful on objects that doesn't resemble any public entity but rather an implementation detail of a complex
     * entity.
     *
     * WARNING: This should be called instead of making direct [MayBeInvalid.validate] calls.
     */
    public fun inline(node: MayBeInvalid)
}