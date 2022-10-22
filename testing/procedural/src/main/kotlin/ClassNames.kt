package com.yandex.daggerlite.testing.procedural

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

internal object ClassNames {
    val BindsInstance = ClassName("com.yandex.daggerlite", "BindsInstance")
    val Component = ClassName("com.yandex.daggerlite", "Component")
    val Module = ClassName("com.yandex.daggerlite", "Module")
    val Lazy = ClassName("com.yandex.daggerlite", "Lazy")
    val Provides = ClassName("com.yandex.daggerlite", "Provides")
    val Binds = ClassName("com.yandex.daggerlite", "Binds")
    val ComponentBuilder = ClassName("com.yandex.daggerlite", "Component", "Builder")
    val Dagger = ClassName("com.yandex.daggerlite", "Dagger")

    val Retention = ClassName("kotlin.annotation", "Retention")
    val AnnotationRetention = ClassName("kotlin.annotation", "AnnotationRetention")

    val Scope = ClassName("javax.inject", "Scope")
    val Inject = ClassName("javax.inject", "Inject")
    val Provider = ClassName("javax.inject", "Provider")

    val mock = MemberName("org.mockito.kotlin", "mock")
    val Answers = ClassName("org.mockito", "Answers")

    val Any = ClassName("kotlin", "Any")
    val Suppress = ClassName("kotlin", "Suppress")

}