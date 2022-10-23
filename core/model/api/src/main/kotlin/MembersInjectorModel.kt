package com.yandex.daggerlite.core.model

import com.yandex.daggerlite.lang.Member
import com.yandex.daggerlite.lang.Method
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * TODO: doc.
 */
interface MembersInjectorModel : MayBeInvalid {
    /**
     * A function (in a component interface) that performs injection
     */
    val injector: Method

    /**
     * The @[javax.inject.Inject]-annotated fields/setters discovered in the injectee.
     */
    val membersToInject: Map<Member, NodeDependency>
}