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

import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method

/**
 * Represents a method declared inside the [component's][ComponentModel] interface, that is intended to perform members
 * injection for the class [type].
 */
public interface MembersInjectorModel : ClassBackedModel {
    /**
     * A function (in a component interface) that performs injection
     */
    public val injector: Method

    /**
     * The @[javax.inject.Inject]-annotated fields/setters discovered in the injectee.
     */
    public val membersToInject: Map<Member, NodeDependency>
}