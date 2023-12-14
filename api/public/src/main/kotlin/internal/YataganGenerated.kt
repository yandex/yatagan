package com.yandex.yatagan.internal

/**
 * Marks top-level classes (currently top-level component implementations) in generated code.
 * May reliably be used in tooling to determine whether the class was generated by Yatagan.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
public annotation class YataganGenerated