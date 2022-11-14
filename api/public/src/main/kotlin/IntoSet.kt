package com.yandex.yatagan

/**
 * A modifier annotation, which behaves largely like [IntoList] with the following differences:
 *
 * - Binds `java.util.Set<[? extends] T>` instead of `List`.
 * - The order of contributions inside the resulting set *is not defined* in any way.
 * - No duplicates (as per `Set` contract) could be present in the set. E.g. if any two `@Provides` return the same
 *  instance/instance that compare equals via `equals` - there'll be only one of them in the set.
 */
public annotation class IntoSet(
    /**
     * Same as [IntoList.flatten].
     */
    val flatten: Boolean = false,
)