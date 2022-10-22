package com.yandex.daggerlite.core.graph.impl

import com.yandex.daggerlite.core.graph.GraphMemberInjector
import com.yandex.daggerlite.core.model.MembersInjectorModel
import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.MemberLangModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendChildContextReference
import com.yandex.daggerlite.validation.format.modelRepresentation
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal class GraphMemberInjectorImpl(
    private val owner: BindingGraphImpl,
    private val impl: MembersInjectorModel,
) : GraphMemberInjector {
    override val injector: FunctionLangModel
        get() = impl.injector

    private val _membersToInject by lazy(PUBLICATION) {
        impl.membersToInject.map { (member, dependency) -> MemberToInjectEntryPoint(member, dependency) }
    }

    override val membersToInject: Map<out MemberLangModel, NodeDependency> by lazy(PUBLICATION) {
        _membersToInject.associateWith { it.dependency }
    }

    private inner class MemberToInjectEntryPoint(
        val member: MemberLangModel,
        override val dependency: NodeDependency,
    ) : MemberLangModel by member, GraphEntryPointBase() {
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