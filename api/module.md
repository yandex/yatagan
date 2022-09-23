# Module api

Contains all client DL API - annotations and helpers. It doesn't contain DL entry-point code
(`com.yandex.daggerlite.Dagger`) though, as that is backend-specific.

One can start reading DL's docs from [the docs on @Component][com.yandex.daggerlite.Component].

It makes sense to use this module as a direct
dependency as a part of a _public api_ of a project module/library. Such choice won't confine clients of the module
to a specific backend kind (compiled or dynamic). It will not, however, allow the code in the module to
_instantiate_ a dagger component. That's usually fine, as it can be done by a client. If that's not acceptable, then
the DL component is rather an implementation detail and concrete API artifact should be used instead.

## API Kinds

- Compiled, see [:api-compiled]
- Dynamic (RT), see [:api-dynamic]

## D2 vs. DL Reference

Note: If any of the following inconsistencies or missing APIs are important to you or block you from using DL in your
project, please, file a feature request, which has a high probability of being implemented.

| Dagger2 API                                   | Status in DL     | Notes                                                                           |
|-----------------------------------------------|------------------|---------------------------------------------------------------------------------|
| `@dagger.Component`                           | (+) as is        |                                                                                 |
| `@dagger.Component.Builder`                   | (+) as is        | supports factory method as well                                                 |
| `@dagger.Component.Factory`                   | (+*) converged   | functionality merged into `@Builder`                                            |
| `@dagger.Subcomponent`                        | (+*) converged   | replaced by [Component(isRoot = false)][com.yandex.daggerlite.Component]        |
| `@dagger.Subcomponent.{Builder/Factory}`      | (+*) converged   | replaced by [Component.Builder][com.yandex.daggerlite.Component.Builder]        |
| `dagger.Lazy`                                 | (+) as is        | now also extends `javax.inject.Provider`                                        |
| `@dagger.Module`                              | (+) as is        |                                                                                 |
| `@dagger.Binds`                               | (+*) tweaked     | can bind zero/multiple alternatives, see [Binds][com.yandex.daggerlite.Binds]   |
| `@dagger.BindsInstance`                       | (+) as is        |                                                                                 |
| `@dagger.Provides`                            | (+) as is        | supports conditional provision                                                  |
| `@dagger.BindsOptionalOf`                     | (+*) replaced    | replaced with [Variant][com.yandex.daggerlite.ComponentVariantDimension] API.   |
| `@dagger.Reusable`                            | (-) unsupported  |                                                                                 |
| `dagger.MembersInjector`                      | (-) unsupported  |                                                                                 |
| `@dagger.MapKey`                              | (+*) renamed     | [IntoMap.Key][com.yandex.daggerlite.IntoMap.Key], `unwrap=false` is unsupported |
| `@dagger.multibindings.IntoSet`               | (+*) renamed     | [IntoList][com.yandex.daggerlite.IntoList], now binds `List<T>`                 |
| `@dagger.multibindings.ElementsIntoSet`       | (+/-*) converged | [IntoList(flatten = true)][com.yandex.daggerlite.IntoList]                      |
| `@dagger.multibindings.Multibinds`            | (+) as is        |                                                                                 |
| `@dagger.multibindings.IntoMap`               | (+) as is        |                                                                                 |
| `@dagger.multibindings.{Int,Class,String}Key` | (+) as is        |                                                                                 |
| `@dagger.multibindings.LongKey`               | (-) removed      | rarely used, can be written by hand at zero cost.                               |
| `dagger.assisted.*`                           | (+) as is        |                                                                                 |
| `dagger.producers.*`                          | (-) unsupported  |                                                                                 |
| `dagger.hilt.*`                               | (-) unsupported  |                                                                                 |
| `dagger.spi.*`                                | (+*) replaced    | DL has its own model for SPI, see [:spi]                                        |

Other behavioral changes:

- [@Binds][com.yandex.daggerlite.Binds] can't be scoped (scope rebind is not allowed). Use scope on the implementation.
  Also, DL supports declaring multiple scopes on bindings.

- DL requires components, builders, assisted inject factories to be declared as interfaces. 
  Abstract classes are forbidden. This is due to the limitations of RT mode.

- If codegen is used, generated component implementations are not named `Dagger<component-name>`,
  their names are mangled, and the access should be made via
  [Dagger.builder()][com.yandex.daggerlite/Dagger#builder@:api-compiled]/
  [Dagger.create()][com.yandex.daggerlite/Dagger#create@:api-compiled] entry-point invocations.
  This is made to support reflection backend.
  See [:api-dynamic] and [:api-compiled] for actual `Dagger` implementations.

- DL does not support `@Nullable` provisions. If a binding returns `null`, or a `@BindsInstance` is supplied with
  `null`, an error will be thrown at run-time. Currently, no compile-time validation is done in the matter.

- Declaring subcomponents now only works explicitly via `Module.subcomponents` list.
  Implicit bindings for subcomponent factory, when declaring entry-point of its type in a parent component,
  are not supported.

- Automatic component factory/builder generation is not supported - an explicit one must be written if required.

- Member inject in Kotlin code should be used with care:
  `@Inject lateinit var prop: SomeClass` will work as expected,
  though `@Inject @Named("id") lateinit var prop: SomeClass` will not - qualifier annotation will go to the *property*
  instead of *field*, and DL will not be able to see it.
  In fact vanilla Dagger will also fail to see it in some scenarios, though it tries to do so on the best-effort basis.
  DL can't read annotations from Kotlin properties, so the following explicit forms should be used instead:
  `@Inject @field:Named("id") lateinit var prop: SomeClass` to inject directly to the field, or
  `@set:Inject @set:Named("id") lateinit var prop: SomeClass` to inject via setter.

## Basic sample

```kotlin
 /*@*/ package test
 /*@*/ import com.yandex.daggerlite.*
 /*@*/ import javax.inject.*
 
 // Assume we have an interface and its implementation 

 interface Api
 class Impl @Inject constructor() : Api
 
 // Add a DL module to a project:
 
 @Module
 interface MyModule {
     @Binds
     fun bindImpl(i: Impl): Api
 }
 
 // Add a DL root component declaration:
 
 @Component(modules = [MyModule::class])
 interface MyComponent {
     val api: Api
 }
 
 // To test a component:
 
 /*@*/ fun test() {
 val myComponent = Dagger.create(MyComponent::class.java)
 assert(myComponent.api is Impl)  
 /*@*/ }

```