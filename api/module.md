# Module api

Contains all client DL API - annotations and helpers. It doesn't contain DL entry-point code
(`com.yandex.daggerlite.Dagger`) though, as that is backend-specific.

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

| Dagger2 API                              | Status in DL    | Notes                                                                         |
|------------------------------------------|-----------------|-------------------------------------------------------------------------------|
| `@dagger.Component`                      | (+) as is       |                                                                               |
| `@dagger.Component.Builder`              | (+) as is       | supports factory method as well                                               |
| `@dagger.Component.Factory`              | (+*) converged  | functionality merged into `@Builder`                                          |
| `@dagger.Subcomponent`                   | (+*) converged  | replaced by [Component(isRoot = false)][com.yandex.daggerlite.Component]      |
| `@dagger.Subcomponent.{Builder/Factory}` | (+*) converged  | replaced by [Component.Builder][com.yandex.daggerlite.Component.Builder]      |
| `dagger.Lazy`                            | (+) as is       | now also extends `javax.inject.Provider`                                      |
| `@dagger.Module`                         | (+) as is       |                                                                               |
| `@dagger.Binds`                          | (+*) tweaked    | can bind zero/multiple alternatives, see [Binds][com.yandex.daggerlite.Binds] |
| `@dagger.BindsInstance`                  | (+) as is       |                                                                               |
| `@dagger.Provides`                       | (+) as is       | supports conditional provision                                                |
| `@dagger.BindsOptionalOf`                | (+*) replaced   | replaced with [Variant][com.yandex.daggerlite.ComponentVariantDimension] API. |
| `@dagger.Reusable`                       | (-) unsupported |                                                                               |
| `dagger.MembersInjector`                 | (-) unsupported |                                                                               |
| `@dagger.MapKey`                         | (-) unsupported | multi-bindings for `Map` are not supported                                    |
| `@dagger.multibindings.IntoSet`          | (+*) renamed    | [IntoList][com.yandex.daggerlite.IntoList], now binds `List<T>`               |
| `@dagger.multibindings.ElementsIntoSet`  | (+*) converged  | [IntoList(flatten = true)][com.yandex.daggerlite.IntoList]                    |
| `@dagger.multibindings.Multibinds`       | (+*) renamed    | [DeclareList][com.yandex.daggerlite.DeclareList]                              |
| `dagger.multibindings.{IntoMap-ish}`     | (-) unsupported | multi-bindings for `Map` are not supported                                    |
| `dagger.assisted.*`                      | (+) as is       |                                                                               |
| `dagger.producers.*`                     | (-) unsupported |                                                                               |
| `dagger.hilt.*`                          | (-) unsupported |                                                                               |
| `dagger.spi.*`                           | (+*) replaced   | DL has its own model for SPI, see [:spi]                                      |

Other behavioral changes:

- DL requires components, builders, assisted inject factories to be declared as interfaces. 
  Abstract classes are forbidden. This is due to the limitations of RT mode.

- If codegen is used, generated component implementations are not named `Dagger<component-name>`,
  their names are mangled, and the access should be made via
  [Dagger.builder()][com.yandex.daggerlite/Dagger#builder@:api-compiled]/
  [Dagger.create()][com.yandex.daggerlite/Dagger#create@:api-compiled] entry-point invocations.
  This is made to support reflection backend.
  See [:api-dynamic] and [:api-compiled] for actual `Dagger` implementations.

- DL does not support `@Nullable` provisions. If a binding returns `null`, or a `@BindsInstance` is supplied with
  `null`, an error run-time will be thrown.

- Declaring subcomponents now only works explicitly via `Module.subcomponents` list.
  Implicit bindings for subcomponent factory, when declaring entry-point of its type in a parent component,
  are not supported.

- Automatic component factory/builder generation is not supported - an explicit one must be written if required.

- `@IntoList` bindings contributions are not inherited from parent component.
  Clients will get "duplicate bindings" error instead. An obscure case, yet worth mentioning.
