package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.MembersInjectorModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.isAnnotatedWith
import com.yandex.daggerlite.core.lang.isGetter
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.reportError
import javax.inject.Inject
import kotlin.LazyThreadSafetyMode.NONE

internal class MembersInjectorModelImpl private constructor(
    override val injector: FunctionLangModel,
) : MembersInjectorModel {
    init {
        require(canRepresent(injector))
    }

    private val injectee = injector.parameters.single().type

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
                it.isAnnotatedWith<Inject>() && it.propertyAccessorInfo?.isGetter != true
            }.forEach { functionInjectee ->
                put(functionInjectee, NodeDependency(
                    type = functionInjectee.parameters.single().type,
                    forQualifier = functionInjectee,
                ))
            }
        }
    }

    override fun validate(validator: Validator) {
        membersToInject.forEach { (_, dependency) ->
            validator.child(dependency.node)
        }
        if (!injector.returnType.isVoid) {
            validator.reportError(Strings.Errors.`non-void injector method return type`(type = injector.returnType))
        }
    }

    companion object Factory : ObjectCache<FunctionLangModel, MembersInjectorModelImpl>() {
        operator fun invoke(injector: FunctionLangModel) = createCached(injector, ::MembersInjectorModelImpl)

        fun canRepresent(impl: FunctionLangModel): Boolean {
            return impl.isAbstract && impl.parameters.count() == 1
        }
    }
}