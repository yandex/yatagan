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
 * Trait for models, that can provide appropriate [NodeModel].
 * NOTE: The main requirement is that the model can be uniquely parsed from a node using
 * [NodeModel.getSpecificModel].
 */
public interface HasNodeModel : ClassBackedModel {
    /**
     *
     * Provides a [node][NodeModel] for this model.
     * Returned node's [superModel][NodeModel.getSpecificModel] is then yields `this`.
     *
     * @return [NodeModel] representing the model.
     */
    public fun asNode(): NodeModel

    public fun <R> accept(visitor: Visitor<R>): R

    public interface Visitor<R> {
        public fun visitDefault(): R
        public fun visitComponent(model: ComponentModel): R = visitDefault()
        public fun visitComponentFactory(model: ComponentFactoryWithBuilderModel): R = visitDefault()
        public fun visitAssistedInjectFactory(model: AssistedInjectFactoryModel): R = visitDefault()
        public fun visitInjectConstructor(model: InjectConstructorModel): R = visitDefault()
        public fun visitConditionExpression(model: InjectedConditionExpressionModel): R = visitDefault()
    }
}