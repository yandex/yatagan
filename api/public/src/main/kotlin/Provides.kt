package com.yandex.yatagan

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
 * /*@*/ import com.yandex.yatagan.*
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
@OptIn(ConditionsApi::class)
public annotation class Provides(
    /**
     * One or more [Conditional] specifiers for this provision.
     */
    vararg val value: Conditional = [],
)