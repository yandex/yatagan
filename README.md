# Dagger Lite

## Summary

Dagger 2 API implementation (with slight changes) with addition of Dynamic conditions API and Component Variant system.
Tuned for build and component initialization performance.

Supports multiple backends first class:

- APT/KAPT
- KSP (experimental, unstable due to KSP utter inconvenience to use for Java code generation)
- Reflection (experimental)

## Differences from vanilla Dagger2 in core API

[Base developer guide][base-doc] for Dagger2 is a good start, as dagger-lite philosophy and API was based on it.

| Vanilla API                            | Status in DL    | Notes                                      |
|----------------------------------------|-----------------|--------------------------------------------|
| `dagger.Component`                     | (+) as is       |                                            |
| `dagger.Component.Builder`             | (+) as is       | supports factory method as well            |
| `dagger.Component.Factory`             | (+*) converged  | functionality merged into `Builder`        |
| `dagger.Subcomponent`                  | (+*) converged  | replaced by `Component(isRoot = false)`    |
| `dagger.Subcomponent.Builder`          | (+*) converged  | replaced by `Component.Builder`            |
| `dagger.Subcomponent.Factory`          | (+*) converged  | replaced by `Component.Builder`            |
| `dagger.Lazy`                          | (+) as is       | now extends `Provider`                     |
| `dagger.Module`                        | (+) as is       |                                            |
| `dagger.Binds`                         | (+*) tweaked    | rebinding scope is not supported           |
| `dagger.BindsInstance`                 | (+) as is       |                                            |
| `dagger.Provides`                      | (+) as is       |                                            |
| `dagger.BindsOptionalOf`               | (+*) replaced   | replaced with Variant/Condition API        |
| `dagger.Reusable`                      | (-) unsupported |                                            |
| `dagger.MembersInjector`               | (-) unsupported |                                            |
| `dagger.MapKey`                        | (-) unsupported | multi-bindings for `Map` are not supported |
| `dagger.multibindings.IntoSet`         | (+*) renamed    | `IntoList`, now binds `List<T>`            |
| `dagger.multibindings.ElementsIntoSet` | (+*) converged  | `IntoList(flatten = true)`                 |
| `dagger.multibindings.Multibinds`      | (+*) renamed    | `DeclareList`                              |
| `dagger.multibindings.{IntoMap-ish}`   | (-) unsupported | multi-bindings for `Map` are not supported |
| `dagger.assisted.*`                    | (-) unsupported |                                            |
| `dagger.producers.*`                   | (-) unsupported |                                            |
| `dagger.hilt.*`                        | (-) unsupported |                                            |
| `dagger.spi.*`                         | (+*) replaced   | dagger-lite has its own model for SPI      |

----------------
Other behavioral changes: 

- Declaring subcomponents now only works explicitly via `Module.subcomponents` list. 
Implicit bindings for subcomponent factory, when declaring entry-point of its type in a parent component, 
are not supported.

- Automatic factory/builder generation is not supported - an explicit one must be written if required.

- `@IntoList` bindings contributions are not inherited from parent component.
Clients will get "duplicate bindings" error instead.

- Generated components are not named `Dagger<component-name>`; the names are mangled, and the access should be made via
`Dagger.builder()`/`Dagger.create()` invocations. This is made to support reflection backend.

## New features

TODO

[base-doc]: https://dagger.dev/dev-guide/