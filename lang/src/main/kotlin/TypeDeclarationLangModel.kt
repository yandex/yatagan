package com.yandex.daggerlite.core.lang

/**
 * Models a type declaration. Can represent class/primitive/array/... types.
 */
interface TypeDeclarationLangModel : AnnotatedLangModel, HasPlatformModel {
    /**
     * Whether the declaration is an interface.
     */
    val isInterface: Boolean

    /**
     * Whether the declaration is abstract (abstract class or interface).
     */
    val isAbstract: Boolean

    /**
     * Whether the declaration is a kotlin `object`.
     */
    val kotlinObjectKind: KotlinObjectKind?

    /**
     * Qualified/Canonical name of the represented class from the Java point of view.
     *
     * Example: `"com.example.TopLevel.Nested"`.
     */
    val qualifiedName: String

    /**
     * If this declaration is nested, returns enclosing declaration. `null` otherwise.
     */
    val enclosingType: TypeDeclarationLangModel?

    /**
     * All implemented interfaces, recursively.
     */
    val implementedInterfaces: Sequence<TypeLangModel>

    /**
     * Declared constructors.
     * Includes only public/internal/package-private constructors.
     */
    val constructors: Sequence<ConstructorLangModel>

    /**
     * Functions (including static and inherited ones, functions from kotlin companion object).
     * Includes only public/internal/package-private functions.
     *
     * All returned functions (including inherited or overridden ones) have [owner][FunctionLangModel.owner] defined
     * as `this`. Only companion object's functions have the [owner][FunctionLangModel.owner] defined as component
     * object declaration and not `this`.
     */
    val functions: Sequence<FunctionLangModel>

    /**
     * Fields (including static). Does NOT include inherited ones.
     * Includes only public/internal/package-private fields.
     */
    val fields: Sequence<FieldLangModel>

    /**
     * Nested classes that are declared inside this declaration.
     * Includes only public/internal/package-private declarations.
     */
    val nestedClasses: Sequence<TypeDeclarationLangModel>

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
     * [com.yandex.daggerlite.Conditional] annotations. If none present, the sequence is empty.
     */
    val conditionals: Sequence<ConditionalAnnotationLangModel>

    /**
     * [com.yandex.daggerlite.ComponentFlavor] annotation if present.
     */
    val componentFlavorIfPresent: ComponentFlavorAnnotationLangModel?
}