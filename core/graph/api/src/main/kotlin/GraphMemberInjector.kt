package com.yandex.daggerlite.core.graph

import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.lang.Member
import com.yandex.daggerlite.lang.Method
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Graph-level abstraction over [MembersInjectorModel][com.yandex.daggerlite.core.MembersInjectorModel].
 */
interface GraphMemberInjector : MayBeInvalid {
    /**
     * See [injector][com.yandex.daggerlite.core.MembersInjectorModel.injector]
     */
    val injector: Method

    /**
     * See [membersToInject][com.yandex.daggerlite.core.MembersInjectorModel.membersToInject]
     */
    val membersToInject: Map<out Member, NodeDependency>
}