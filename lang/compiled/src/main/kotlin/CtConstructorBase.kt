package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.AssistedInject
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.common.ConstructorBase
import javax.inject.Inject

abstract class CtConstructorBase : ConstructorBase() {
    override fun <T : BuiltinAnnotation.OnConstructor> getAnnotation(
        which: BuiltinAnnotation.Target.OnConstructor<T>,
    ): T? {
        val value: BuiltinAnnotation.OnConstructor? = when (which) {
            BuiltinAnnotation.AssistedInject -> (which as BuiltinAnnotation.AssistedInject)
                .takeIf { annotations.any { it.hasType<AssistedInject>() } }

            BuiltinAnnotation.Inject -> (which as BuiltinAnnotation.Inject)
                .takeIf { annotations.any { it.hasType<Inject>() } }
        }

        return which.modelClass.cast(value)
    }
}