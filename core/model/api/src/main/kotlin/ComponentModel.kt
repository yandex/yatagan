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

import com.yandex.yatagan.base.api.Incubating

/**
 * Represents @[com.yandex.yatagan.Component] annotated class - Component.
 */
public interface ComponentModel : ConditionalHoldingModel, HasNodeModel {
    /**
     * A set of *all* modules that are included into the component (transitively).
     */
    public val modules: Set<ModuleModel>

    /**
     * All supported scopes for bindings, that component can cache.
     */
    public val scopes: Set<ScopeModel>

    /**
     * A set of component *dependencies*.
     */
    public val dependencies: Set<ComponentDependencyModel>

    /**
     * A list of [ComponentEntryPoint]s in the component.
     */
    public val entryPoints: List<ComponentEntryPoint>

    /**
     * A list of [MembersInjectorModel]s defined for this component.
     */
    public val memberInjectors: List<MembersInjectorModel>

    /**
     * A list of [ComponentFactoryModel]s declared in this component for its children components.
     */
    public val subComponentFactoryMethods: List<SubComponentFactoryMethodModel>

    /**
     * An optional explicit factory for this component creation.
     */
    public val factory: ComponentFactoryWithBuilderModel?

    /**
     * Whether this component is marked as a component hierarchy root.
     * Do not confuse with [ComponentModel.dependencies] - these are different types of component relations.
     */
    public val isRoot: Boolean

    /**
     * TODO: doc.
     */
    @Incubating
    public val variant: Variant

    /**
     * `true` if this component entry-points and/or their dependencies can be accessed from multiple threads.
     * `false` otherwise.
     */
    public val requiresSynchronizedAccess: Boolean
}