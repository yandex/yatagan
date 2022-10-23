package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.Binds
import com.yandex.daggerlite.BindsInstance
import com.yandex.daggerlite.IntoList
import com.yandex.daggerlite.IntoMap
import com.yandex.daggerlite.IntoSet
import com.yandex.daggerlite.Multibinds
import com.yandex.daggerlite.Provides
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.common.MethodBase
import javax.inject.Inject

abstract class CtMethod : MethodBase() {
    abstract override val annotations: Sequence<CtAnnotation>

    override fun <T : BuiltinAnnotation.OnMethod> getAnnotation(
        which: BuiltinAnnotation.Target.OnMethod<T>
    ): T? {
        val annotation: BuiltinAnnotation.OnMethod? = when (which) {
            BuiltinAnnotation.Binds -> (which as BuiltinAnnotation.Binds)
                .takeIf { annotations.any { it.hasType<Binds>() } }
            BuiltinAnnotation.BindsInstance -> (which as BuiltinAnnotation.BindsInstance)
                .takeIf { annotations.any { it.hasType<BindsInstance>() } }
            BuiltinAnnotation.Provides ->
                annotations.find { it.hasType<Provides>() }?.let { CtProvidesAnnotationImpl(it) }
            BuiltinAnnotation.IntoMap -> (which as BuiltinAnnotation.IntoMap)
                .takeIf { annotations.any { it.hasType<IntoMap>() } }
            BuiltinAnnotation.Multibinds -> (which as BuiltinAnnotation.Multibinds)
                .takeIf { annotations.any { it.hasType<Multibinds>() } }
            BuiltinAnnotation.Inject -> (which as BuiltinAnnotation.Inject)
                .takeIf { annotations.any { it.hasType<Inject>() } }
        }
        return which.modelClass.cast(annotation)
    }

    override fun <T : BuiltinAnnotation.OnMethodRepeatable> getAnnotations(
        which: BuiltinAnnotation.Target.OnMethodRepeatable<T>
    ): List<T> {
        return when (which) {
            BuiltinAnnotation.IntoCollectionFamily -> buildList {
                for (annotation in annotations) {
                    when {
                        annotation.hasType<IntoList>() ->
                            add(which.modelClass.cast(CtIntoListAnnotationImpl(annotation)))
                        annotation.hasType<IntoSet>() ->
                            add(which.modelClass.cast(CtIntoSetAnnotationImpl(annotation)))
                    }
                }
            }
        }
    }
}