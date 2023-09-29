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

import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents a function/property exposed from a component interface.
 *
 * @see ComponentModel.entryPoints
 */
public interface ComponentEntryPoint : MayBeInvalid {
    /**
     * Method in the component interface corresponding to the entry-point.
     */
    public val getter: Method

    /**
     * A parsed [getter]s return value.
     */
    public val dependency: NodeDependency
}