package com.yandex.yatagan.core.model

import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid

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