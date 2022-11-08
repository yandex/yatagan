package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.IntoMap
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.common.AnnotationDeclarationBase
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