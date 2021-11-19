package com.yandex.daggerlite.core.lang

/**
 * Models a type declaration. Can represent class/primitive/array/... types.
 */
interface TypeDeclarationLangModel : AnnotatedLangModel {
    /**
     * Whether the declaration is abstract (abstract class or interface).
     */
    val isAbstract: Boolean

    /**
     * Whether the declaration is a kotlin `object`.
     */
    val isKotlinObject: Boolean

    /**
     * Qualified/Canonical name of the represented class from the Java point of view.
     */
    val qualifiedName: String

    /**
     * All implemented interfaces, recursively.
     */
    val implementedInterfaces: Sequence<TypeLangModel>

    /**
     * All constructors declared.
     */
    val constructors: Sequence<FunctionLangModel>

    /**
     * All public functions (including static and inherited ones).
     */
    val allPublicFunctions: Sequence<FunctionLangModel>

    /**
     * All public fields (including static and inherited ones).
     */
    val allPublicFields: Sequence<FieldLangModel>

    /**
     * Interfaces that are declared inside this declaration.
     */
    val nestedInterfaces: Sequence<TypeDeclarationLangModel>

    /**
     * Creates [TypeLangModel] based on the declaration **assuming, that no type arguments are required**.
     * If the declaration does require type arguments, the behavior is undefined.
     */
    fun asType(): TypeLangModel

    /**
     * [com.yandex.daggerlite.Component] annotation if present.
     */
    val componentAnnotationIfPresent: ComponentAnnotationLangModel?

    /**
     * [com.yandex.daggerlite.Module] annotation if present.
     */
    val moduleAnnotationIfPresent: ModuleAnnotationLangModel?

    /**
     * Aggregated [com.yandex.daggerlite.Condition], ... etc. annotations.
     */
    val conditions: Sequence<ConditionLangModel>

    /**
     * TODO: doc.
     */
    val conditionals: Sequence<ConditionalAnnotationLangModel>

    /**
     * TODO: doc.
     */
    val componentFlavorIfPresent: ComponentFlavorAnnotationLangModel?
}