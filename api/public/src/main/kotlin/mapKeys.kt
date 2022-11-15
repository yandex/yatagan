package com.yandex.yatagan

import kotlin.reflect.KClass

/**
 * Builtin [IntoMap.Key]-annotation for unconstrained `Class` keys.
 */
@IntoMap.Key
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class ClassKey(val value: KClass<*>)

/**
 * Builtin [IntoMap.Key]-annotation for `int` keys.
 */
@IntoMap.Key
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class IntKey(val value: Int)

/**
 * Builtin [IntoMap.Key]-annotation for `String` keys.
 */
@IntoMap.Key
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
public annotation class StringKey(val value: String)
