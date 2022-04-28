package com.yandex.daggerlite

import kotlin.reflect.KClass

// region Core API

/**
 * Annotates a class/object/interface that contains explicit bindings that contribute to the object graph.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Module(
    /**
     * Additional [Modules][Module] to be transitively included into a [Component]/another [Module].
     * Allows duplicates, recursively.
     */
    val includes: Array<KClass<*>> = [],

    /**
     * [Component]-annotated interfaces, that should be children in a [Component] which includes this
     * module.
     *
     * Allows duplicates, recursively via [includes].
     *
     * Any included [components][Component] must have [Component.isRoot] to be set to `false`.
     */
    val subcomponents: Array<KClass<*>> = [],
)

/**
 * A **binding** declaration marker.
 *
 * Annotates *abstract* methods of a [Module] that *delegate* bindings.
 * In general, method parameters' (if any, see below) types must be assignable to the return type.
 *
 * There are three cases for `@Binds` in dagger-lite:
 * - a method with *exactly one parameter* (e. g. `@Binds fun api(i: Impl): Api`) constitutes
 * an **alias** binding. Wherever `Api` is requested, `Impl` will be injected.
 * ```kotlin
 * // The general idea behind the ALIAS binding, though @Binds will behave more efficiently.
 * @Provides
 * fun api(i: Impl): Api = i
 * ```
 *
 * - a method with *more than one parameter* (e. g. `@Binds fun api(i1: Impl1, i2: Impl2, stub: Stub): Api`)
 * constitutes an **alternatives** binding. Wherever `Api` is requested, *the first available alternative*
 * (in the order of declaration) will be injected. Availability is checked in terms of [condition system][Conditional].
 * ```kotlin
 * // This is the general idea behind ALTERNATIVES binding, though @Binds will behave more efficiently.
 * // This doesn't handle Provider/Lazy/Optional dependencies, as @Binds does.
 * @Provides
 * fun api(i1: Optional<Provider<Impl1>>, i2: Optional<Provider<Impl2>>, stub: Provider<Stub>): Api {
 *     if (i1.isPresent) {
 *         return i1.get().get()
 *     }
 *     if (i2.isPresent) {
 *         return i2.get().get()
 *     }
 *     return stub.get()
 * }
 * ```
 * - a method with *zero parameters* (e. g. `@Binds fun noApi(): Api`) constitutes an **explicit absent** binding.
 * Its condition will be intrinsic *"never"* condition - it would be an error to request `Api` directly, and all
 * [Optional] requests will always yield [Optional.empty].
 * ```kotlin
 * // This is the general idea behind EXPLICIT ABSENT binding.
 * // The following code is more of a pseudocode, as such @Provides is not valid in terms of dagger-lite
 * @Provides
 * fun noApi(): Optional<Api> = Optional.empty()
 * ```
 *
 * `@Binds` bindings family is never implemented or called - they just carry the necessary type info for the framework.
 *
 *
 * All these cases can be [qualified][javax.inject.Qualifier].
 * The **alternatives** can also be [scoped][javax.inject.Scope].
 * All these cases can be [multi-bindings][IntoList].
 *
 * @see Provides
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Binds(
)

/**
 * A **binding** declaration marker.
 *
 * Annotates *non-abstract* methods of a [Module] that can be called to *provide* an instance of the requested graph
 * node's type - a **provision**.
 *
 * Annotated method's parameters are treated as dependencies and are injected as usual.
 *
 * Examples:
 * ```
 * /** provides Application instance from static context */
 * @Provides myExternalClass(): Application {
 *   return ApplicationManager.getApplication()
 * }
 *
 * /** creates SomeClass instance with custom factory and dependencies */
 * @Provides @Singleton someInstance(
 *   factory: CustomFactory,
 *   dep1: Provider<Foo>,
 *   dep2: Optional<Bar>,
 * ): SomeClass {
 *   return factory.create(dep1, bar)
 * }
 * ```
 * Provisions can be [qualified][javax.inject.Qualifier], [scoped][javax.inject.Scope] and
 * can be [multi-binding][IntoList] contribution. **Provisions can not return `null`**.
 * If it's necessary to create optional bindings, use either [Conditional], or custom Optional-like wrapper types.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
)
annotation class Provides(
    /**
     * One or more [Conditional] specifiers for this provision.
     */
    val value: Array<Conditional> = [],
)

/**
 * Annotation marker for dagger-lite component interface declarations.
 *
 * A component declaration can declare:
 * - entry-point getter methods/`val` properties, e. g:
 *   ```kotlin
 *   @Component
 *   interface MyComponent {
 *     // no parameters.
 *     fun getMyClassA(): ClassA                      // direct dependency
 *     val myClassB: Lazy<ClassB>                     // lazy dependency
 *     @Named("hello") val myClassC: Provider<ClassC> // provider for qualified dependency.
 *     ...
 *   }
 *   ```
 * - member injection methods, e. g.
 *   ```kotlin
 *   @Component
 *   interface MyComponent {
 *     ..
 *     fun injectMembers(intoInstance: MyView)  // void/Unit return type, a single parameter.
 *   }
 *   ```
 *   where `MyView` may be declared like this:
 *   ```kotlin
 *   class MyView(...) : ... {
 *     @Inject
 *     fun setA(a: ClassA)
 *
 *     @set:Inject
 *     lateinit var optionalB: Optional<Provider<ClassB>>
 *
 *     @set:Inject @set:Named("hello")  // Note, that `set` annotation target is mandatory!
 *     lateinit var providerForB: Lazy<ClassC>
 *     ...
 *   }
 *   ```
 *   It's convenient when dependencies from the graph are required in a externally created instance -
 *   then the member-injection method will invoke all @Inject annotated **declared** setters with dependencies.
 *
 * - component creator (builder/factory) interface, e. g.
 *   ```kotlin
 *   @Component
 *   interface MyComponent
 *      // Optional for root component, mandatory for non-root components.
 *      @Component.Builder
 *      interface Creator {
 *          // TODO: dependencies, modules.
 *          @BindsInstance   // to introduce pre-created instance to the graph, builder-like.
 *          fun setApplication(app: Application): Creator  // void/Unit return type would also work.
 *          fun create(
 *              @BindsInstance activity: Activity,  // to introduce pre-created instance to the graph, factory-like.
 *          ): MyComponent  // creation method, mandatory.
 *      }
 *   ```
 *
 *
 * To instantiate a component, clients should depend on *backend-specific* API artifact,
 * like `com.yandex.daggerlite:api-compiled:<ver>` if they want to use **code-generated component implementations**.
 * There's also an **experimental reflection-based** backend - `com.yandex.daggerlite:api-dynamic:<ver>`.
 *
 * With those artifacts, to instantiate a root component with an explicit creator, use the following code
 * ```kotlin
 * import com.yandex.daggerlite.Dagger
 * // this will create component implementation. This API works for every backend.
 * val component: MyComponent = Dagger.builder(MyComponent.Builder::class.java).create()
 * ```
 *
 * TODO: Scope, Sub-/Super-components, Dependencies.
 *
 * @see isRoot
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Component(
    /**
     * If `true` (default), then the component is a root of a hierarchy and may be created directly.
     * Otherwise, the component serves as a subcomponent and must be installed into its parent via
     * [Module.subcomponents] attribute.
     */
    val isRoot: Boolean = true,

    /**
     * A list of modules to include into the component.
     */
    val modules: Array<KClass<*>> = [],

    /**
     * A list of *component dependencies* for the component.
     *
     * @see Component
     */
    val dependencies: Array<KClass<*>> = [],

    /**
     * A list of [flavors][ComponentFlavor].
     * No more than one flavor per dimension may be present in a variant.
     * If this component is a child, then the effective variant will be extended by the parent's effective variant.
     * Thus, a child can't declare flavors for dimensions, that are already declared in parents.
     *
     * The main idea behind the *variant system* is to be able to have the following hierarchies (example):
     * ```kotlin
     * @Component interface MyApplicationComponent
     *
     * interface MyUiComponent { ... }
     *
     * @Component(isRoot = false, variant = [Device.Phone::class]
     * interface MyPhoneUiComponent : MyUiComponent { ... }
     *
     * @Component(isRoot = false, variant = [Device.Watch::class]
     * interface MyWatchUiComponent : MyUiComponent { ... }
     * ```
     *
     * @see Conditional
     */
    val variant: Array<KClass<*>> = [],

    /**
     * If `true`, then the component's implementation is guaranteed to be thread-safe.
     * If `false`, then the implementation is not thread-safe yet may have increased performance in a
     * single-thread environment. For single-thread implementations, the thread-access is checked via
     * [ThreadAssertions].
     */
    val multiThreadAccess: Boolean = false,
) {
    /**
     * Annotates component creator interface declaration, which should be nested inside component declaration.
     *
     * @see Component
     */
    @MustBeDocumented
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    annotation class Builder
}

/**
 * Used in [Component.Builder] declaration to annotate setters or factory arguments.
 * The types (may be qualified) are then accessible in the graph.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
)
annotation class BindsInstance(
)

// endregion Core API

// region Conditions API

/**
 * A boolean literal in terms of [Conditional] system.
 * Denotes a single boolean condition, accessible from a static scope, which can be evaluated at runtime.
 *
 * This condition is fully opaque in terms of the framework and its correctness checks.
 * Conditions are considered equal by the [value] and parsed [condition].
 * If two conditions are not equal, then they are treated as
 * completely unrelated conditions and no guesses are further made.
 *
 * This annotation is repeatable forming up an [AllConditions] container - repeating
 * annotations are `AND`ed together.
 * To build an 'OR' condition use [AnyCondition] annotation, which is also repeatable and all
 * it's instances are `AND`ed together in [AnyConditions] container.
 * So in general case the resulting expression is a conjunction ('AND') of disjunctions ('OR') or,
 * in other words, Conjunctive Normal Form.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
@JvmRepeatable(AllConditions::class)
annotation class Condition(
    /**
     * A root class from which a condition accessor chain is built.
     */
    val value: KClass<*>,

    /**
     * A string consisting from plain Java identifiers, separated by '.' and optionally staring with
     * '!'.
     *
     * Each identifier in chain represents a non-private field, method or property, resolved on a
     * resulting type of previous identifier or the root class if this is the first identifier.
     * The last identifier must resolve into a primitive boolean result.
     *
     * If starts with '!', the result value will be negated.
     */
    val condition: String,
)

/**
 * Container annotation for [Condition].
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class AllConditions(
    val value: Array<Condition>,
)

/**
 * Logical `||` operator for [Conditions][Condition].
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
@JvmRepeatable(AnyConditions::class)
annotation class AnyCondition(
    val value: Array<Condition>
)

/**
 * Container annotation for [AnyCondition].
 *
 * @see Condition
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class AnyConditions(
    val value: Array<AnyCondition>,
)

/**
 * Container annotation for [Conditional].
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Conditionals(
    val value: Array<Conditional>,
)

/**
 * Specifies that an annotated entity (a class with [javax.inject.Inject] constructor or a provision method)
 * should be present in a graph under certain conditions.
 *
 * @see value
 * @see onlyIn
 */
@MustBeDocumented
@JvmRepeatable(Conditionals::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Conditional(
    /**
     * A list of **feature declarations** - annotation types, annotated with
     * [Condition]-family annotations to form an expression.
     *
     * The features in this list are `&&`-ed together.
     */
    val value: Array<KClass<out Annotation>> = [],

    /**
     * Component flavor constraint within *component flavor system*,
     * which constitutes a compile-time condition.
     *
     * A list of [ComponentFlavor]-annotated annotation types.
     *
     * ## Examples:
     * Assume the following [flavors][ComponentFlavor] and [dimensions][ComponentVariantDimension] are declared:
     *
     * ```kotlin
     * @ComponentVariantDimension annotation class Product {
     *   @ComponentFlavor(dimension = Product::class) annotation class FooApp
     *   @ComponentFlavor(dimension = Product::class) annotation class BarApp
     * }
     * @ComponentVariantDimension annotation class Device {
     *   @ComponentFlavor(dimension = Device::class) annotation class Tablet
     *   @ComponentFlavor(dimension = Device::class) annotation class Phone
     *   @ComponentFlavor(dimension = Device::class) annotation class Watch
     * }
     * ```
     *
     * Simple runtime condition - entity is accessible in every component under FeatureA:
     * ```kotlin
     * @Conditional([FeatureA::class])
     * ```
     *
     * Simple compile-time filter - entity is accessible only in components, that declare Device.Watch flavor:
     * ```kotlin
     * @Conditional(onlyIn = [Device.Watch::class])
     * ```
     *
     * Entity is accessible only in components that declare `Device.Phone` in their [variant][Component.variant].
     * Inaccessible anywhere else.
     * ```kotlin
     * @Conditional([FeatureA::class], onlyIn = [Device.Phone::class])
     * ```
     *
     * More complex example with multiple conditionals:
     * ```kotlin
     * @Conditional([FeatureA::class, FeatureB::class], onlyIn = [
     *   Product.FooApp,
     *   Device.Phone, Device.Tablet,
     * ])  // accessible in FooApp on phones and tablets (but not on watches) under FeatureA && FeatureB.
     * @Conditional([FeatureC::class], onlyIn = [
     *   Product.BarApp
     * ])  // accessible in BarApp (in all form-factors) under FeatureC.
     * ```
     *
     * @see [Component.variant]
     */
    val onlyIn: Array<KClass<*>> = [],
)

/**
 * Annotates an annotation class that denotes a component variant dimension.
 * Later any number of [flavors][ComponentFlavor] may be associated with this dimension.
 *
 * @see Component.variant
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class ComponentVariantDimension

/**
 * Annotates an annotation class that denotes a component flavor.
 * The flavor must be associated with a [dimension][ComponentVariantDimension].
 *
 * It's a good practice to declare all basic flavors for a dimension inside the dimension declaration for better
 * visual association.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class ComponentFlavor(
    /**
     * Dimension, that this flavor belongs to.
     */
    val dimension: KClass<out Annotation>,
)

// endregion Conditions API

// region Assisted Inject API

/**
 * TODO: doc.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CONSTRUCTOR)
annotation class AssistedInject

/**
 * TODO: doc.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Assisted(
    /**
     * TODO: doc.
     */
    val value: String = "",
)

/**
 * TODO: doc.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class AssistedFactory

// endregion

/**
 * TODO: doc.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class DeclareList(
)

/**
 * TODO: doc.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class IntoList(
    /**
     * TODO: doc.
     */
    val flatten: Boolean = false,
)