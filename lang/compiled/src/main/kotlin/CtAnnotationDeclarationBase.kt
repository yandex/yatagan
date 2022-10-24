package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.IntoMap
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.common.AnnotationDeclarationBase
import javax.inject.Qualifier
import javax.inject.Scope

abstract class CtAnnotationDeclarationBase : AnnotationDeclarationBase() {
    override fun <T : BuiltinAnnotation.OnAnnotationClass> getAnnotation(
        builtinAnnotation: BuiltinAnnotation.Target.OnAnnotationClass<T>,
    ): T? {
        val annotation: BuiltinAnnotation.OnAnnotationClass? = when(builtinAnnotation) {
            BuiltinAnnotation.IntoMap.Key -> (builtinAnnotation as BuiltinAnnotation.IntoMap.Key)
                .takeIf { annotations.any { it.hasType<IntoMap.Key>() } }
            BuiltinAnnotation.Qualifier -> (builtinAnnotation as BuiltinAnnotation.Qualifier)
                .takeIf { annotations.any { it.hasType<Qualifier>() } }
            BuiltinAnnotation.Scope -> (builtinAnnotation as BuiltinAnnotation.Scope)
                .takeIf { annotations.any { it.hasType<Scope>() } }
        }
        return builtinAnnotation.modelClass.cast(annotation)
    }
}