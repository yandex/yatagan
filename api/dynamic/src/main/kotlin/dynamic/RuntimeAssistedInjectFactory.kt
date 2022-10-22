package com.yandex.daggerlite.dynamic

import com.yandex.daggerlite.DynamicValidationDelegate
import com.yandex.daggerlite.core.model.AssistedInjectFactoryModel
import com.yandex.daggerlite.lang.rt.rt

internal class RuntimeAssistedInjectFactory(
    private val model: AssistedInjectFactoryModel,
    private val owner: RuntimeComponent,
    validationPromise: DynamicValidationDelegate.Promise?,
) : InvocationHandlerBase(validationPromise) {
    init {
        model.factoryMethod?.let { factoryMethod ->
            implementMethod(factoryMethod.rt, FactoryMethodHandler())
        }
    }

    private inner class FactoryMethodHandler : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): Any? {
            val params = model.assistedConstructorParameters
            return checkNotNull(model.assistedInjectConstructor).rt.newInstance(
                *Array(params.size) { index ->
                    when (val parameter = params[index]) {
                        is AssistedInjectFactoryModel.Parameter.Assisted -> {
                            val argIndex = model.assistedFactoryParameters.indexOf(parameter)
                            check(argIndex >= 0)
                            args!![argIndex]
                        }
                        is AssistedInjectFactoryModel.Parameter.Injected -> {
                            owner.resolveAndAccess(parameter.dependency)
                        }
                    }
                }
            )
        }
    }
}