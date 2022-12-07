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

package com.yandex.yatagan.core.model

/**
 * Formally denotes graph edges' type.
 * Informally a [NodeModel] can depend on other [NodeModel]s with this kind of dependency.
 *
 * @see NodeDependency
 */
public enum class DependencyKind {
    /**
     * Type is requested directly (eagerly).
     */
    Direct,

    /**
     * [com.yandex.yatagan.Lazy]-wrapped type is requested.
     */
    Lazy,

    /**
     * [javax.inject.Provider]-wrapped type is requested.
     */
    Provider,

    /**
     * [com.yandex.yatagan.Optional]-wrapped type is requested.
     */
    Optional,

    /**
     * `Optional<Lazy<...>>`-wrapped type is requested.
     *
     * @see com.yandex.yatagan.Optional
     * @see com.yandex.yatagan.Lazy
     */
    OptionalLazy,

    /**
     * `Optional<Provider<...>>`-wrapped type is requested.
     *
     * @see com.yandex.yatagan.Optional
     * @see javax.inject.Provider
     */
    OptionalProvider,
}