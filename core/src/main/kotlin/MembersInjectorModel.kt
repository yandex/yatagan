package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

/**
 * TODO: doc.
 */
interface MembersInjectorModel {
    /**
     * A function (in a component interface) that performs injection
     */
    val injector: FunctionLangModel

    /**
     * An [injector]'s single parameter's type, the target of the injection.
     */
    val injectee: TypeLangModel

    /**
     * The @[javax.inject.Inject]-annotated fields/setters discovered in [injectee].
     */
    val membersToInject: Map<MemberLangModel, NodeDependency>
}