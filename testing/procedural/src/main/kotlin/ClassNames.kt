package com.yandex.yatagan.testing.procedural

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

internal object ClassNames {
    val BindsInstance = ClassName("com.yandex.yatagan", "BindsInstance")
    val Component = ClassName("com.yandex.yatagan", "Component")
    val Module = ClassName("com.yandex.yatagan", "Module")
    val Lazy = ClassName("com.yandex.yatagan", "Lazy")
    val Provides = ClassName("com.yandex.yatagan", "Provides")
    val Binds = ClassName("com.yandex.yatagan", "Binds")
    val ComponentBuilder = ClassName("com.yandex.yatagan", "Component", "Builder")
    val Dagger = ClassName("com.yandex.yatagan", "Dagger")

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