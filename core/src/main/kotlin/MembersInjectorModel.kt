package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * TODO: doc.
 */
interface MembersInjectorModel : MayBeInvalid {
    /**
     * A function (in a component interface) that performs injection
     */
    val injector: FunctionLangModel

    /**
     * The @[javax.inject.Inject]-annotated fields/setters discovered in the injectee.
     */
    val membersToInject: Map<MemberLangModel, NodeDependency>
}