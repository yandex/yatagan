package com.yandex.daggerlite.graph

import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Graph-level abstraction over [MembersInjectorModel][com.yandex.daggerlite.core.MembersInjectorModel].
 */
interface GraphMemberInjector : MayBeInvalid {
    /**
     * See [injector][com.yandex.daggerlite.core.MembersInjectorModel.injector]
     */
    val injector: FunctionLangModel

    /**
     * See [membersToInject][com.yandex.daggerlite.core.MembersInjectorModel.membersToInject]
     */
    val membersToInject: Map<out MemberLangModel, NodeDependency>
}