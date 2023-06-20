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
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents a parsed runtime condition model.
 */
@Incubating
public interface ConditionModel : ConditionExpression.Literal, MayBeInvalid {
    /**
     * A class/object/companion that contains the first member from [path].
     * [NodeModel] type is used to be able to resolve the instance of a graph in case of
     * [non-static condition][requiresInstance].
     */
    public val root: NodeModel

    /**
     * A chain of members, that, if evaluated sequentially on the result of each other,  lead to boolean value.
     * The first member if evaluated on [root].
     */
    public val path: List<Member>

    /**
     * `true` if this condition requires an injectable instance to evaluate (non-static condition).
     * `false` if condition can be evaluated from the static context (plain/static condition).
     */
    public val requiresInstance: Boolean

    /**
     * Negates the literal and returns the result.
     *
     * @return the same condition model yet with [negated] flipped.
     */
    override fun not(): ConditionModel
}