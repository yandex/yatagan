package com.yandex.yatagan

/**
 * See the D2 [docs](https://dagger.dev/api/latest/dagger/assisted/Assisted.html), behavior should be identical.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Assisted(
    /**
     * See the D2 [docs](https://dagger.dev/api/latest/dagger/assisted/Assisted.html#value--),
     * behavior should be identical.
     */
    val value: String = "",
)