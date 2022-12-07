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

package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.GraphMemberInjector
import com.yandex.yatagan.core.model.MembersInjectorModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class GraphMemberInjectorImpl(
    private val owner: BindingGraphImpl,
    private val impl: MembersInjectorModel,
) : GraphMemberInjector {
    override val injector: Method
        get() = impl.injector

    private val _membersToInject by lazy(PUBLICATION) {
        impl.membersToInject.map { (member, dependency) -> MemberToInjectEntryPoint(member, dependency) }
    }

    override val membersToInject: Map<out Member, NodeDependency> by lazy(PUBLICATION) {
        _membersToInject.associateWith { it.dependency }
    }

    private inner class MemberToInjectEntryPoint(
        val member: Member,
        override val dependency: NodeDependency,
    ) : Member by member, GraphEntryPointBase() {
        override val graph: BindingGraphImpl
            get() = this@GraphMemberInjectorImpl.owner

        override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
            modelClassName = "member-to-inject",
            representation = {
                append("${member.name}: ")
                if (childContext != null) {
                    appendChildContextReference(reference = dependency)
                } else {
                    append(dependency)
                }
            }
        )
    }

    override fun validate(validator: Validator) {
        _membersToInject.forEach(validator::child)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "member-injector",
        representation = {
            append("${injector.name}(${injector.parameters.single().type}")
            if (childContext is MemberToInjectEntryPoint) {
                append(" { .., ")
                appendChildContextReference(reference = childContext.member.name)
                append(", .. }")
            }
            append(")")
        },
    )
}