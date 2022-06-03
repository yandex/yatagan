package com.yandex.daggerlite

import kotlin.reflect.KClass

// region Core API

/**
 * Annotates a class/object/interface that contains explicit bindings that contribute to the object graph.
 *
 * If Module declaration contain a *companion object with the default name*, methods from it are also treated like
 * static bindings.
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
 * `@Binds` usage variants by example:
 *
 * ```kotlin
 * /*@*/ package test
 * /*@*/ import com.yandex.daggerlite.*
 * /*@*/ import javax.inject.*
 *
 * /*@*/ interface Api
 * /*@*/ class Impl1 @Inject constructor() : Api {}
 * /*@*/ class Impl2 @Inject constructor() : Api {}
 * /*@*/ class Stub @Inject constructor() : Api {}
 *
 * /*@*/ @Module interface TestModule1 {
 * // `@Binds` methods with a single parameter constitutes an **alias** binding:
 * // whenever `Api` is requested, `Impl` is injected.
 * // This is the basic and the most common usage:
 * @Binds
 * fun aliasForApi(i: Impl1): Api
 * /*@*/ }
 *
 * // Other variants are more advanced cases, that are part of the *Conditions API*.
 *
 * // `@Binds` methods with *more than one* parameter constitute an **alternatives** binding:
 * /*@*/ @Module interface TestModule2 {
 * @Binds
 * fun alternatives(impl: Impl1, orThis: Impl2, fallback: Stub): Api
 * /*@*/}
 *
 * // `@Binds` methods with *zero* parameters constitute an explicit **empty** or **absent** binding.
 * // Directly requesting `Api` will always be a condition violation,
 * // while `Optional<Api>` dependencies will always be *empty*.
 * /*@*/ @Module interface TestModule3 {
 * @Binds
 * fun noApi(): Api
 * /*@*/}
 *
 * /*@*/ @Component(modules=[TestModule1::class]) interface TestComponent1 { val api: Api }
 * /*@*/ @Component(modules=[TestModule2::class]) interface TestComponent2 { val api: Api }
 * /*@*/ @Component(modules=[TestModule3::class]) interface TestComponent3 { val api: Optional<Api> }
 * ```
 * All these cases can be [qualified][javax.inject.Qualifier].
 * The **alternatives** can also be [scoped][javax.inject.Scope].
 * All these cases can be [multi-bindings][IntoList].
 *
 * @see Provides
 * @see Optional
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
 * ```kotlin
 * /*@*/ package test
 * /*@*/ import com.yandex.daggerlite.*
 * /*@*/ import javax.inject.*
 * /*@*/ class Application
 * /*@*/ object ApplicationManager { fun getApplication() = Application() }
 * /*@*/ class SomeClass; class Foo; class Bar
 * /*@*/ interface CustomFactory { fun create(d1: Provider<Foo>, d2: Optional<Bar>): SomeClass }
 *
 * /** provides Application instance from static context */
 * @Provides
 * fun myExternalClass(): Application {
 *   return ApplicationManager.getApplication()
 * }
 *
 * /** creates SomeClass instance with custom factory and dependencies */
 * @Provides @Singleton
 * fun someInstance(
 *   factory: CustomFactory,
 *   dep1: Provider<Foo>,
 *   dep2: Optional<Bar>,
 * ): SomeClass {
 *   return factory.create(dep1, dep2)
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
    vararg val value: Conditional = [],
)

/**
 * Interfaces, annotated with `@Component` are called *Component declarations*.
 * Component interface declarations will be implemented (generated or proxied) by DL framework.
 *
 * The instances of these generated implementations are called DL *components* - they host DI graph and are ready to
 * construct and provide/inject *dependencies*.
 *
 * ## Entry-points
 * A Component declaration contains *entry-points* declarations - abstract functions/readonly-properties.
 * Framework then tries to find ways to construct instances of types, that entry-points have.
 * These "ways" are expressed by *bindings*.
 *
 * ## Member-injectors
 * Methods, that return `void`/`Unit` and accept a single parameter of type,
 * that has public [Inject][javax.inject.Inject]-annotated setters/fields.
 * These members' types are treated as dependencies and injected by the framework,
 * when an instance is supplied to the member-injector.
 *
 * ## Bindings
 * A binding essentially carries the information on *how to instantiate a particular type*.
 * Some bindings are *intrinsic* and automatically inferred (e.g. component instance),
 * some are *explicit* and written by hand (e. g. [Binds], [Provides] or [Inject][javax.inject.Inject], ..)
 *
 * Some bindings can be *scoped*.
 *
 * There can be only one binding per component hierarchy for the specific node (type + qualifier pair).
 * Multiple bindings will raise an error.
 *
 * ## Dependencies
 * If there's a binding for a type `T`, then the framework can inject it not only directly,
 * but also wrapped into [Lazy], [Provider][javax.inject.Provider] or [Optional].
 * Also `Optional<Lazy<T>>` and `Optional<Provider<T>>` combinations are allowed.
 * `Lazy<Optional<T>>`, `Lazy<Lazy<T>>`, etc. are not allowed.
 *
 * [Optional] wrapper type is a part of the [*Conditions API*][Condition].
 *
 * Writing explicit bindings for these *framework types* are not allowed -
 * they are managed exclusively by the framework.
 *
 * ## Scopes
 * Scopes manage, *where* and *how many times* an instance of a type can be created by the framework.
 * If a binding is bound to a scope by using [Scope][javax.inject.Scope]-annotated annotation,
 * then **it is cached inside the scope**. For such bindings, `Lazy` and `Provider` injections have no difference.
 *
 * For *unscoped* bindings, an instance will be created on every request;
 * `Lazy` can locally cache such bindings.
 *
 * ## Qualifiers
 * Every type can be additionally qualified with [Qualifier][javax.inject.Qualifier]-annotated annotation.
 * The most common built-in qualifier annotation is [Named][javax.inject.Named].
 *
 * Example component declaration (with accompanying declarations):
 * ```kotlin
 * /*@*/ package test
 * /*@*/ import com.yandex.daggerlite.*
 * /*@*/ import javax.inject.*
 *
 * class ClassA @Inject constructor(b: ClassB)
 * class ClassB @Inject constructor(@Named("hello") c: ClassC)
 * class ClassC (val id: String)
 *
 * class ClassD {
 *   @Inject
 *   fun setA(a: ClassA) { println(a) }
 *
 *   @set:Inject  // Note, that `set` annotation target is mandatory!
 *   lateinit var optionalB: Optional<Provider<ClassB>>
 *
 *   @set:Inject @set:Named("hello")  // Note, that `set` annotation target is mandatory!
 *   lateinit var providerForB: Lazy<ClassC>
 * }
 *
 * @Module
 * object MyModule {
 *   @Provides
 *   @Named("hello")
 *   fun classC(): ClassC { return ClassC("hello") }
 * }
 *
 * @Component(modules = [MyModule::class])
 * interface MyComponent {
 *   fun getMyClassA(): ClassA                      // direct dependency
 *   val myClassB: Lazy<ClassB>                     // lazy dependency
 *   @get:Named("hello")
 *   val myClassC: Provider<ClassC>                 // provider for qualified dependency
 *   fun injectD(d: ClassD)                         // member-injector for ClassD
 * }
 * ```
 *
 * ## Component builders
 * See [Component.Builder].
 *
 * ## Component hierarchy
 * TODO: Coming soon
 *
 * ## Component dependencies
 * TODO: Coming soon
 *
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
     * A list of modules to install into the component, recursively using [Module.includes].
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
     * /*@*/ package test
     * /*@*/ import com.yandex.daggerlite.*
     * /*@*/ @ComponentVariantDimension annotation class Device {
     * /*@*/   @ComponentFlavor(dimension = Device::class) annotation class Tablet
     * /*@*/   @ComponentFlavor(dimension = Device::class) annotation class Phone
     * /*@*/   @ComponentFlavor(dimension = Device::class) annotation class Watch
     * /*@*/ }
     *
     * @Component
     * interface MyApplicationComponent
     *
     * interface MyUiComponent { /* some entry-point */ }
     *
     * @Component(isRoot = false, variant = [Device.Phone::class])
     * interface MyPhoneUiComponent : MyUiComponent
     *
     * @Component(isRoot = false, variant = [Device.Watch::class])
     * interface MyWatchUiComponent : MyUiComponent
     * ```
     *
     * @see Conditional
     */
    val variant: Array<KClass<*>> = [],

    /**
     * If `true`, then the component's implementation is guaranteed to be thread-safe.
     * If `false`, then the implementation is not thread-safe;
     * Furthermore, every [Lazy]/[javax.inject.Provider] issued by the component is not thread-safe.
     *
     * thread-unsafe implementations *may* have increased performance in a single-thread environment.
     * For single-thread implementations, the thread-access is checked via [ThreadAssertions].
     */
    val multiThreadAccess: Boolean = false,
) {
    /**
     * Annotates *component creator interface* declaration, which should be nested inside component declaration.
     * Creator interfaces can act in factory-like fashion, having arguments declared in its *factory method*;
     * Builder-like usage is also supported.
     *
     * Arguments (in setters and factory method) can be:
     * - [Module] instances
     * - [BindsInstance]-annotated instances, that constitute an explicit *instance binding*.
     * - Component dependencies' instances.
     *
     * Factory method is mandatory.
     *
     * Component creator interface is optional if component doesn't have any component dependencies;
     * modules, that require instance; instance bindings;
     * Component creator interface is mandatory for non-root components.
     *
     * Example:
     * ```kotlin
     * /*@*/ import com.yandex.daggerlite.*
     * /*@*/ import javax.inject.*
     *
     * @Component
     * interface TestComponent {
     *   @Component.Builder
     *   interface Creator {
     *     @BindsInstance @Named("ratio") fun setRatio(ratio: Double): Creator
     *     @BindsInstance @Named("count") fun setCount(count: Int)
     *     fun create(
     *       @BindsInstance @Named("id") id: Int,
     *       @BindsInstance @Named("string") name: String,
     *     ): TestComponent
     *   }
     * }
     * ```
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
     * Each identifier in chain represents a non-private field, method, resolved on a
     * resulting type of previous identifier or the root class if this is the first identifier.
     * The last identifier must resolve into a primitive boolean result.
     *
     * Names are considered from Java point of view, not Kotlin! So property names are not a thing, getters should be
     * used instead.
     *
     * If starts with '!', the result value will be negated.
     *
     * Examples:
     * ```kotlin
     * /*@*/ package test
     * /*@*/ import com.yandex.daggerlite.*
     * /*@*/ import javax.inject.*
     * /*@*/ object SomeObject { val isEnabled = false }
     * /*@*/ object SomeClass {
     * /*@*/   @JvmStatic fun staticComputeCondition() = false
     * /*@*/   @JvmStatic fun staticGetSubObject() = AnotherClass()
     * /*@*/ }
     * /*@*/ class AnotherClass { val memberCondition = false }
     * /*@*/ class WithCompanion { companion object { val prop = false } }
     *
     * // Kotlin's singletons require explicit INSTANCE identifier:
     * @Condition(SomeObject::class, "INSTANCE.isEnabled")
     * /*@*/ annotation class A
     *
     * // Static function from a class:
     * @Condition(SomeClass::class, "staticComputeCondition")
     * /*@*/ annotation class B
     *
     * // Calls can be chained. Properties are accessible through explicit getters:
     * @Condition(SomeClass::class, "staticGetSubObject.getMemberCondition")
     * /*@*/ annotation class C
     *
     * // Companion object must be mentioned explicitly:
     * @Condition(WithCompanion::class, "Companion.getProp")
     * /*@*/ annotation class D
     *
     * /*@*/ @Conditional(A::class, B::class, C::class, D::class)
     * /*@*/ class Sample @Inject constructor() {}
     * /*@*/ @Component interface SampleComponent { val s: Optional<Sample> }
     * /*@*/ fun test() { Dagger.create(SampleComponent::class.java).s }
     * ```
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
    vararg val value: Condition,
)

/**
 * Logical `||` operator for [Conditions][Condition].
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
@JvmRepeatable(AnyConditions::class)
annotation class AnyCondition(
    vararg val value: Condition,
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
    vararg val value: AnyCondition,
)

/**
 * Container annotation for [Conditional].
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Conditionals(
    vararg val value: Conditional,
)

/**
 * Specifies that an associated entity (a class with [javax.inject.Inject] constructor/a [provision][Provides]/
 * a [child component][Module.subcomponents])
 * should be present in a graph **under certain runtime [conditions][Condition]**.
 *
 * Types under condition are permitted to be directly injected only into the types
 *  under the same or *implying* condition.
 * **The condition implication checking is enforced by the framework** at graph building time.
 * If a client is not under condition or under different condition, then [Optional] dependency is required to be able
 * to inject a conditional type.
 *
 * ```kotlin
 * /*@*/ import com.yandex.daggerlite.*
 * /*@*/ import javax.inject.*
 **
 * /*@*/object Flags {
 * /*@*/ var flagA: Boolean = true
 * /*@*/ var flagB: Boolean = false
 * /*@*/}
 *
 * // Assume we have the following features declared:
 *
 * @Condition(Flags::class, "flagA") annotation class FeatureA
 * @Condition(Flags::class, "flagB") annotation class FeatureB
 * @AllConditions(
 *  Condition(Flags::class, "flagA"),
 *  Condition(Flags::class, "flagB"),
 * )
 * annotation class FeatureAB
 *
 * // Then for a class under `A` it's only allowed to inject class under `B` wrapped in `Optional`:
 * @Conditional(FeatureA::class)
 * class UnderA @Inject constructor(
 *   val b: Optional<UnderB>
 * )
 *
 * // And so goes for an `A` from `B`:
 * @Conditional(FeatureB::class)
 * class UnderB @Inject constructor(
 *   val a: Optional<Provider<UnderA>>
 * )
 *
 * // Yet it's okay to inject `UnderA`, `UnderB` into a class under `A && B` feature,
 * // because `A && B` implies both `A` and `B`, so no condition violation is possible.
 * @Conditional(FeatureAB::class)
 * class UnderAB @Inject constructor(
 *   val a: UnderA,
 *   val b: UnderB,
 * )
 * ```
 *
 * @see value
 * @see onlyIn
 * @see Binds
 * @see Provides.value
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
    vararg val value: KClass<out Annotation> = [],

    /**
     * Component flavor constraint within *Variant API*,
     * which constitutes a compile-time condition.
     *
     * A list of [ComponentFlavor]-annotated annotation types.
     *
     * ## Examples:
     * ```kotlin
     * /*@*/ import com.yandex.daggerlite.*
     * /*@*/ import javax.inject.*
     *
     * /*@*/ object Flags { var a = false; var b = false; var c = false  }
     * /*@*/ @Condition(Flags::class, "a") annotation class FeatureA
     * /*@*/ @Condition(Flags::class, "b") annotation class FeatureB
     * /*@*/ @Condition(Flags::class, "c") annotation class FeatureC
     *
     * // Assume the following *flavors* and *dimensions* are declared:
     * @ComponentVariantDimension annotation class Product {
     *   @ComponentFlavor(dimension = Product::class) annotation class FooApp
     *   @ComponentFlavor(dimension = Product::class) annotation class BarApp
     * }
     * @ComponentVariantDimension annotation class Device {
     *   @ComponentFlavor(dimension = Device::class) annotation class Tablet
     *   @ComponentFlavor(dimension = Device::class) annotation class Phone
     *   @ComponentFlavor(dimension = Device::class) annotation class Watch
     * }
     *
     * // Simple runtime condition - entity is accessible in every component under FeatureA:
     * @Conditional(FeatureA::class)
     * class UnderFeatureA @Inject constructor()
     *
     * // Simple compile-time filter - entity is accessible only in components, that declare Device.Watch flavor:
     * @Conditional(onlyIn = [Device.Watch::class])
     * class WatchSpecific @Inject constructor()
     *
     * // Entity is accessible only in components that declare `Device.Phone` in their [variant][Component.variant].
     * // Inaccessible anywhere else:
     * @Conditional(FeatureA::class, onlyIn = [Device.Phone::class])
     * class PhoneSpecificUnderFeatureA @Inject constructor()
     *
     * // More complex example with multiple conditionals:
     * @Conditional(FeatureA::class, FeatureB::class, onlyIn = [
     *   Product.FooApp::class,
     *   Device.Phone::class, Device.Tablet::class,
     * ])  // accessible in FooApp on phones and tablets (but not on watches) under FeatureA && FeatureB.
     * @Conditional(FeatureC::class, onlyIn = [
     *   Product.BarApp::class
     * ])  // accessible in BarApp (in all form-factors) under FeatureC.
     * class Complex @Inject constructor()
     * ```
     *
     * @see Component.variant
     * @see ComponentFlavor
     * @see ComponentVariantDimension
     */
    val onlyIn: Array<KClass<*>> = [],
)

/**
 * Annotates an annotation class that denotes a component variant dimension.
 * Any number of [flavors][ComponentFlavor] may be associated with this dimension.
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
 * See the D2 [docs](https://dagger.dev/api/latest/dagger/assisted/AssistedInject.html), behavior should be identical.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CONSTRUCTOR)
annotation class AssistedInject

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

/**
 * See the D2 [docs](https://dagger.dev/api/latest/dagger/assisted/AssistedFactory.html), behavior should be identical.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class AssistedFactory

// endregion

/**
 *
 * Helps to just declare a multi-bound list to a type in case *there's no actual multi-bindings to that type*, so
 * that DL won't complain with about a missing binding. If no bindings are present for a list, then an emply list
 * is provided.
 *
 * Example:
 * ```kotlin
 * /*@*/ package test
 * /*@*/ import com.yandex.daggerlite.*
 * @Module
 * interface SomeModule {
 *   @DeclareList
 *   fun listOfNumbers(): Number
 * }
 *
 * // Possible component declaration:
 *
 * @Component(modules = [SomeModule::class])
 * interface ExampleComponent {
 *   val numbers: List<Number>
 * }
 *
 * // Then the following holds true:
 *
 * /*@*/ fun test() {
 * /*@*/assert(
 * Dagger.create(ExampleComponent::class.java).numbers.isEmpty()
 * /*@*/)
 * /*@*/}
 * ```
 *
 * @see IntoList
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class DeclareList(
)

/**
 * A special modifier annotation that can be applied together with [Binds] or [Provides].
 *
 * When applied, a *binding* becomes a *multi-binding* - a list of the bound type is introduced to the graph.
 * Therese can be multiple `IntoList` bindings for a given node (type + qualifier).
 * The list will contain *all* instances, provided by the bindings.
 *
 * The order is well-defined: the instances will be topologically-sorted according to their binding's dependencies.
 *  (independent bindings are sorted alphabetically).
 *
 * The multi-binding's return type does not "spill" outside the list and does not conflict with other non-multi
 * binding for the same type.
 *
 * Let's assume we have the following bindings
 * ```kotlin
 * /*@*/ package test
 * /*@*/ import com.yandex.daggerlite.*
 * /*@*/ import javax.inject.*
 *
 * /*@*/@Module interface TestModule {
 * /*@*/ companion object {
 *
 * @IntoList
 * @Provides
 * fun provideOne(): Number = 1.1f
 *
 * @Provides
 * @Named("two")
 * fun someDouble(): Double = 2.0
 *
 * @IntoList(flatten = true)
 * @Provides
 * fun numbers(): Collection<Number> = listOf(3, -4L, 5)
 *
 * /*@*/}
 *
 * @IntoList
 * @Binds
 * fun bindTwo(@Named("two") d: Double): Number
 *
 * /*@*/}
 *
 * // Then the following entry-point may be declared inside the component:
 * /*@*/ @Component(modules = [TestModule::class]) interface ExampleComponent {
 * val numbers: List<Number>
 * /*@*/}
 *
 * // And the following will hold true for the list:
 *
 * /*@*/ fun test() {
 * /*@*/ val component = Dagger.create(ExampleComponent::class.java)
 * /*@*/assert(
 * component.numbers == listOf<Number>(1.1f, 2.0, 3, -4L, 5)
 * /*@*/)
 * /*@*/}
 *
 * ```
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class IntoList(
    /**
     * If this is set to `true`, then multi-binding
     * treats return type as a collection, elements of which will all be contributed to the multi-bound list.
     *
     * The return type actually *must* be compatible with a `Collection` interface.
     *
     * See example [here][IntoList], that includes usage of this flag.
     */
    val flatten: Boolean = false,
)