package com.yandex.yatagan

import kotlin.reflect.KClass

/**
 * Interfaces, annotated with `@Component` are called *Component declarations*.
 * Component interface declarations will be implemented (generated or proxied) by Yatagan framework.
 *
 * The instances of these generated implementations are called Yatagan *components* - they host DI graph and are ready to
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
 * If a binding is bound to a scope (or multiple scopes) by using [Scope][javax.inject.Scope]-annotated annotation(s),
 * then **it is cached inside the scope**. For such bindings, `Lazy` and `Provider` injections have no difference.
 *
 * Scoped bindings can only be used inside a component of the same scope.
 * If a binding is marked by more than one scope it is compatible with any component that has at least one scope from
 * the list (binding's and component's scopes intersection is non-empty).
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
 * /*@*/ import com.yandex.yatagan.*
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
     * /*@*/ import com.yandex.yatagan.*
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
    val variant: Array<KClass<out Annotation>> = [],

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
     * /*@*/ import com.yandex.yatagan.*
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