package com.yandex.daggerlite.lang

/**
 * Denotes type declaration kind.
 */
enum class TypeDeclarationKind {
    /**
     * No declaration is logically present for the type.
     * Arrays, primitive types, void, etc.
     */
    None,

    /**
     * `class` declaration.
     */
    Class,

    /**
     * `enum` class declaration.
     */
    Enum,

    /**
     * `interface` declaration.
     */
    Interface,

    /**
     * `@interface`/`annotation class` declaration.
     */
    Annotation,

    /**
     * Kotlin-specific: `object` declaration.
     */
    KotlinObject,

    /**
     * Kotlin-specific `companion object` declaration.
     *
     * NOTE: only companions with default name `Companion` are recognized due to compatibility reasons.
     */
    KotlinCompanion,
}