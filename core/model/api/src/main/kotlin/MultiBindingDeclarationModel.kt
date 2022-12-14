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
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * A [com.yandex.yatagan.Multibinds] model.
 */
public interface MultiBindingDeclarationModel : MayBeInvalid {

    public fun <R> accept(visitor: Visitor<R>): R

    public interface Visitor<R> {
        public fun visitCollectionDeclaration(model: CollectionDeclarationModel): R
        public fun visitMapDeclaration(model: MapDeclarationModel): R
        public fun visitInvalid(model: InvalidDeclarationModel): R
    }

    /**
     * Declares empty list binding
     */
    public interface CollectionDeclarationModel : MultiBindingDeclarationModel {
        /**
         * An element's type for a multi-bound list.
         */
        public val elementType: NodeModel?

        /**
         * Target collection kind for a multi-binding.
         */
        public val kind: CollectionTargetKind
    }

    /**
     * Declares empty map binding
     */
    public interface MapDeclarationModel : MultiBindingDeclarationModel {
        public val keyType: Type?
        public val valueType: NodeModel?
    }

    /**
     * Denotes invalid [com.yandex.yatagan.Multibinds] with unrecognized return value.
     */
    public interface InvalidDeclarationModel : MultiBindingDeclarationModel {
        public val invalidMethod: Method
    }
}