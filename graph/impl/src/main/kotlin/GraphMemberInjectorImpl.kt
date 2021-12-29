package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.MembersInjectorModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.GraphMemberInjector
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import kotlin.LazyThreadSafetyMode.NONE

internal class GraphMemberInjectorImpl(
    private val owner: BindingGraph,
    private val impl: MembersInjectorModel,
) : GraphMemberInjector {
    override val injector: FunctionLangModel
        get() = impl.injector

    private val _membersToInject by lazy(NONE) {
        impl.membersToInject.map { (member, dependency) -> MemberToInject(member, dependency) }
    }

    override val membersToInject: Map<out MemberLangModel, NodeDependency> by lazy(NONE) {
        _membersToInject.associateWith { it.dependency }
    }

    private inner class MemberToInject(
        val member: MemberLangModel,
        val dependency: NodeDependency,
    ) : MemberLangModel by member, MayBeInvalid {

        override fun validate(validator: Validator) {
            validator.child(owner.resolveBinding(dependency.node))
        }

        override fun toString() = "[member-to-inject] ${member.name}"
    }

    override fun validate(validator: Validator) {
        _membersToInject.forEach(validator::child)
    }

    override fun toString() = "[injector-fun] ${injector.name}"
}