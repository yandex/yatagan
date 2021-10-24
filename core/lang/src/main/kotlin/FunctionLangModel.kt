package com.yandex.daggerlite.core.lang

/**
 * Represents a function/method associated with a class from **the Java point of view**.
 * - Can represent a constructor, see [isConstructor]
 * - Top-level kotlin functions are not covered.
 * - Kotlin properties are covered by `get{name.capitalize()}`/`set{name.capitalize()}`.
 */
interface FunctionLangModel : AnnotatedLangModel {
    /**
     * Type that this function is associated with. Constructee if [isConstructor].
     */
    val owner: TypeDeclarationLangModel

    /**
     * Whether the function is the constructor. Then [returnType] and [owner] are the sane type - constructee.
     */
    val isConstructor: Boolean

    /**
     * Whether the function is abstract.
     */
    val isAbstract: Boolean

    /**
     * Whether the function is truly static (@[JvmStatic] or `static`).
     */
    val isStatic: Boolean

    /**
     * Return type of the function.
     */
    val returnType: TypeLangModel

    /**
     * Function name.
     */
    val name: String

    /**
     * Function parameters.
     */
    val parameters: Sequence<ParameterLangModel>
}