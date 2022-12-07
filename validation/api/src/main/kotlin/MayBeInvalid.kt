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
 * An interface for the models, which are eligible for validation (which may be invalid).
 * Models, implementing the interface, are nodes of a **validation graph**.
 * Implementing classes should have human-readable [toString] implementation,
 * as it is used to specify validation messages origin.

 * # For external clients
 *
 * This API is merely used as an extension point to report validation messages, that can be seamlessly plugged into
 * Yatagan's internal validation pipeline
 *
 * # For Yatagan internal validation pipeline
 *
 * Model implementations should internally support two states:
 *   - valid state where all contracts of the API are met.
 *   In valid state a node makes no calls to [Validator.report] with an [Error][ValidationMessage.Kind.Error] kind.
 *
 *   - invalid state where contracts of the API are met on the best-effort basis with a focus on minimizing
 *   induced (ghost) errors in other models, that are implemented in top of this model.
 *   In invalid state a node makes one or more calls to [Validator.report] with an
 *   [Error][ValidationMessage.Kind.Error] kind.
 *
 * - Regardless of the validity state a node may call [Validator.inline]/[Validator.child] to trace a validation graph.
 *
 * @see Validator
 */
public interface MayBeInvalid {
    /**
     * `accept`-like method with a [Validator] as visitor.
     * Implementations should report their validity state and build validation graph.
     *
     * NOTE: This method should only make decisions based on internal model state, without taking environment into
     * account or producing any side effects ("Purity" requirement).
     *
     * @param validator tracing validator object.
     */
    public fun validate(validator: Validator)

    /**
     * @return string representation of the node within an optional context.
     * @param childContext an optional validation graph node, that this node reported as a [child][Validator.child].
     *  an instance *may be* used to provide more laconic string representation.
     */
    public fun toString(childContext: MayBeInvalid?): CharSequence
}