package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

/**
 * TODO: doc.
 */
interface MembersInjectorModel {
    val injector: FunctionLangModel

    val injectee: TypeLangModel

    val membersToInject: Map<MemberLangModel, NodeDependency>
}