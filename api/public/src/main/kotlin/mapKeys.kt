package com.yandex.daggerlite

import kotlin.reflect.KClass

/**
 * Builtin [IntoMap.Key]-annotation for unconstrained `Class` keys.
 */
@IntoMap.Key
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ClassKey(val value: KClass<*>)

/**
 * Builtin [IntoMap.Key]-annotation for `int` keys.
 */
@IntoMap.Key
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class IntKey(val value: Int)

/**
 * Builtin [IntoMap.Key]-annotation for `String` keys.
 */
@IntoMap.Key
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class StringKey(val value: String)
