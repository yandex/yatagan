package com.yandex.daggerlite.lang

/**
 * Models a type declaration. Can represent class/primitive/array/... types.
 *
 * As of now type declaration has 1 : 1 relation to the [type][asType].
 * This allows the declaration members be presented with already resolved class-level generics and eliminated the need
 * for a public API for `asMemberOf()` and the likes. This should be taken into account while comparing two type
 * declarations for equality - they may compare unequal for they have different underlying types.
 */
interface TypeDeclarationLangModel : AnnotatedLangModel, HasPlatformModel, Accessible,
    Comparable<TypeDeclarationLangModel> {
    /**
     * Declaration kind.
     */
    val kind: TypeDeclarationKind

    /**
     * Whether the declaration is abstract (abstract class or interface).
     */
    val isAbstract: Boolean

    /**
     * Qualified/Canonical name of the represented class from the Java point of view.
     *
     * Example: `"com.example.TopLevel.Nested"`, `"int"`, `"void"`, ...
     */
    val qualifiedName: String

    /**
     * If this declaration is nested, returns enclosing declaration. `null` otherwise.
     */
    val enclosingType: TypeDeclarationLangModel?

    /**
     * Interfaces directly implemented/extended by the declaration.
     */
    val interfaces: Sequence<TypeLangModel>

    /**
     * Super-type, if present.
     *
     * NOTE: Never returns `java.lang.Object`/`kotlin.Any`, `null` is returned instead.
     * This is done to counter uniformity issues.
     */
    val superType: TypeLangModel?

    /**
     * All declared non-private constructors.
     */
    val constructors: Sequence<ConstructorLangModel>

    /**
     * All non-private functions (including static and inherited ones).
     *
     * All returned functions (including inherited or overridden ones) have [owner][FunctionLangModel.owner] defined
     * as `this`.
     *
     * Never includes functions defined in `java.lang.Object`/`kotlin.Any`, as they are of no interest to DL.
     */
    val functions: Sequence<FunctionLangModel>

    /**
     * All non-private declared fields (including static and inherited).
     */
    val fields: Sequence<FieldLangModel>

    /**
     * Nested non-private classes that are declared inside this declaration.
     */
    val nestedClasses: Sequence<TypeDeclarationLangModel>

    /**
     * Kotlin's default companion object declaration, if one exists for the type.
     * If a companion object has non-default name (`"Companion"`), it won't be found here.
     */
    val defaultCompanionObjectDeclaration: TypeDeclarationLangModel?

    /**
     * Returns an underlying [TypeLangModel].
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