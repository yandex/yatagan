package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.MembersInjectorModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import javax.inject.Inject
import kotlin.LazyThreadSafetyMode.NONE

internal class MembersInjectorModelImpl private constructor(
    override val injector: FunctionLangModel,
) : MembersInjectorModel {
    init {
        require(canRepresent(injector))
    }

    override val injectee = injector.parameters.single().type

    override val membersToInject: Map<MemberLangModel, NodeDependency> by lazy(NONE) {
        buildMap {
            injectee.declaration.allPublicFields.filter {
                it.isAnnotatedWith<Inject>()
            }.forEach { fieldInjectee ->
                put(fieldInjectee, NodeDependency(
                    type = fieldInjectee.type,
                    forQualifier = fieldInjectee,
                ))
            }
            injectee.declaration.allPublicFunctions.filter {
                it.isAnnotatedWith<Inject>() &&
                        // TODO: this is rather a requirement than a filter condition; though it can fail on
                        //  kotlin properties. So either model kotlin property separately or live like this.
                        it.parameters.count() == 1
            }.forEach { functionInjectee ->
                put(functionInjectee, NodeDependency(
                    type = functionInjectee.parameters.single().type,
                    forQualifier = functionInjectee,
                ))
            }
        }
    }

    companion object Factory : ObjectCache<FunctionLangModel, MembersInjectorModelImpl>() {
        operator fun invoke(injector: FunctionLangModel) = createCached(injector, ::MembersInjectorModelImpl)

        fun canRepresent(impl: FunctionLangModel): Boolean {
            return impl.isAbstract && impl.parameters.count() == 1
        }
    }
}