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

package com.yandex.yatagan.core.graph.bindings

import com.yandex.yatagan.core.model.NodeModel

/**
 * A binding that can override/extend a binding with the same [target] from the parent graph.
 */
public interface ExtensibleBinding<B> : Binding where B : ExtensibleBinding<B> {
    /**
     * An optional reference to a binding from one of the parent graphs, to include contributions from.
     */
    public val upstream: B?

    /**
     * A special intrinsic node, which is used for downstream binding to depend on this binding
     *  (as its [upstream]).
     *
     * Any downstream bindings' dependencies must include this node.
     */
    public val targetForDownstream: NodeModel
}