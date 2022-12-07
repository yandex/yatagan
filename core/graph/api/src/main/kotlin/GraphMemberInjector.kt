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

package com.yandex.yatagan.core.graph

import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Graph-level abstraction over [MembersInjectorModel][com.yandex.yatagan.core.MembersInjectorModel].
 */
public interface GraphMemberInjector : MayBeInvalid {
    /**
     * See [injector][com.yandex.yatagan.core.MembersInjectorModel.injector]
     */
    public val injector: Method

    /**
     * See [membersToInject][com.yandex.yatagan.core.MembersInjectorModel.membersToInject]
     */
    public val membersToInject: Map<out Member, NodeDependency>
}