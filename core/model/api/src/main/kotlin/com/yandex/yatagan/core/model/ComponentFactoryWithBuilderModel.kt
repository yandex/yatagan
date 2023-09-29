/*
 * Copyright 2023 Yandex LLC
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

import com.yandex.yatagan.lang.Method

/**
 * Represents [com.yandex.yatagan.Component.Builder]. Can be injected.
 */
public interface ComponentFactoryWithBuilderModel : ComponentFactoryModel, HasNodeModel {
    /**
     * Inputs from [setters][BuilderInputModel.builderSetter] in terms of builder pattern.
     */
    public val builderInputs: Collection<BuilderInputModel>

    /**
     * Represents a setter method for the input in builder-like fashion.
     */
    public interface BuilderInputModel : ComponentFactoryModel.InputModel {
        /**
         * Actual abstract setter.
         * Has *single parameter*; return type may be of the builder type itself or
         * [void][com.yandex.yatagan.lang.Type.isVoid].
         */
        public val builderSetter: Method
    }
}