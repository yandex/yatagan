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

package com.yandex.yatagan.core.graph

import com.yandex.yatagan.base.api.NullIfInvalid
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.SubComponentFactoryMethodModel
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Graph-level abstraction over [com.yandex.yatagan.core.model.ComponentFactoryModel] obtained from
 * [com.yandex.yatagan.core.model.ComponentModel.subComponentFactoryMethods].
 */
public interface GraphSubComponentFactoryMethod : MayBeInvalid {
    /**
     * Underlying model.
     */
    public val model: SubComponentFactoryMethodModel

    /**
     * Graph object build from [model.createdComponent][ComponentFactoryModel.createdComponent].
     */
    @NullIfInvalid
    public val createdGraph: BindingGraph?
}