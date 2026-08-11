# Yatagan

[![Maven Central](https://img.shields.io/maven-central/v/com.yandex.yatagan/api-public)](https://central.sonatype.com/artifact/com.yandex.yatagan/api-public)
[![CI](https://github.com/yandex/yatagan/actions/workflows/main.yaml/badge.svg)](https://github.com/yandex/yatagan/actions/workflows/main.yaml)
[![codecov](https://codecov.io/gh/yandex/yatagan/graph/badge.svg?token=XW9AVMQWM0)](https://codecov.io/gh/yandex/yatagan)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Yatagan is a fast **Dependency Injection framework** based on Google's [Dagger2][D2] API.

Yatagan is optimized for fast builds and supports managing large dynamic dependency graphs by introducing
[_Conditions_ and _Variants_](#added-apis).
It's primary goal is to **improve build speed in large complex projects, which already use Dagger**.
Though it might be useful for others.

All core Dagger API is supported with little changes.
Yet dagger-android, Hilt and a couple of less popular features are not supported.
See comparative [API reference](#yatagan-vs-dagger-api-reference) for full info.

Yatagan also has an experimental
[Dagger compatibility mode](#dagger-compatibility-mode-experimental), which allows it to act as a drop-in
replacement for Dagger with minimal migration work.

Yatagan can work in multiple modes (use different _backends_):

- With code generation
    - APT/KAPT - classic mode.
    - KSP - leverages Google [KSP][KSP] framework, see [KSP notes](#ksp-support).
- Via runtime Java reflection - a backend designed for fast local debug builds, see
  specific [notes](#reflection-support).

All backends are designed to be working _identically_, so one can easily switch backends depending on the
working environment and requirements. Any inconsistencies between backends ought to be reported and fixed.

## Motivation

One can consider migrating to Yatagan from vanilla Dagger if at least one of the points is true for their project:

1. The project uses Dagger and has **build performance issues** because of it.
2. The project needs/extensively uses **dynamic optional dependencies** in its DI graphs.

Yatagan tackles both of these issues.

Read more and dive into technical details in the Medium [article][MEDIUM]. 

### Performance

As of the first point, performance gains can vary per project due to specific details and Yatagan usage configuration.
Yatagan allows clients to **apply processing to fewer project modules** in comparison to Dagger.
Yatagan processing should only be applied to project modules, that contain _root components_,
and shouldn't be applied at all in case of reflection mode usage.

Thus, a project will have the biggest performance gain from using Yatagan, if the majority of the project modules
have only one annotation processor - Dagger. Then upon migration to Yatagan project modules without root components can
have kapt completely switched off with it remaining in root-component modules. Furthermore, root-component modules can
also become kapt-free with Reflection mode.
In a good scenario Yatagan can make incremental builds **up to two times faster**.

If other annotation processors besides Dagger are used throughout the project in KAPT mode,
then performance gain from using Yatagan will be lower.
One can try and reorganise the project to limit other annotation processors appliance to small modules or
use them in KSP mode if supported.
Reflection mode is also supported for some frameworks that feature code generation.
It can be enabled in debug builds if this allows to eliminate KAPT from the project module.

The general idea is to remove KAPT from as many modules as possible, with large modules yielding more profit, so
feel free to experiment with what Yatagan offers for this task.

In the worst case scenario, where using Yatagan doesn't remove KAPT from any of the modules, profits can still be around
~ 10% due to Yatagan optimizations.

### Runtime conditions

The second point can be clarified by the following code snippet:

```kotlin
@Singleton
class MyClass @Inject constructor(
    /**
     * Dynamic optional dependency, that is present/absent in DI-graph based on declared runtime condition.
     */
    val myOptionalDependency: Optional<MyClassUnderRuntimeCondition>,
)
```

This is one of the approaches that can be taken into coding optional dependencies.
Naturally, such thing can be written with Dagger's `@Provides Optional<MyClassUnderRuntimeCondition> provide(..) { .. }`
yet such code is arguably difficult to maintain, verbose,
and scales poorly with the growing number of conditions and classes under them.

Yatagan solves this by introducing first-class runtime condition support with compile-time condition validation.
See [_Conditions_/_Variants_ APIs](#added-apis).

## Usage (Gradle)

Code generation dependency is only required for project modules, that contain root component declarations
(`@Component(isRoot = true/* default */)`). For modules, that contain classes with `@Inject`, `@Provides`, etc..
no dependencies but "api" ones are required.
This is different for Dagger, which requires you to apply annotation processing in every module with DI code to
allow Gradle incremental compilation/annotation processing to work correctly.

Yatagan can be used in various configurations. Choose one, that suits your project.
See the following Gradle buildscript usage snippets (code is assumed to be inside a `dependencies {}` block).

For kotlin-only/mixed project using **kapt**:

```kotlin
// Ensure `kotlin-kapt` plugin is applied
api("com.yandex.yatagan:api-public:${yataganVer}")
// kapt is slow but generally reliable for mixed projects.
kapt("com.yandex.yatagan:processor-jap:${yataganVer}")
```

For kotlin-only/mixed project using **KSP** (use with caution for Java code):
([How to](https://kotlinlang.org/docs/ksp-quickstart.html#use-your-own-processor-in-a-project) apply KSP plugin)

```kotlin
// Ensure `com.google.devtools.ksp` plugin is applied
api("com.yandex.yatagan:api-public:${yataganVer}")
// KSP works best for pure-Kotlin projects.
ksp("com.yandex.yatagan:processor-ksp:${yataganVer}")
```

To dramatically speed up build one can use **runtime reflection** instead of codegen:

```kotlin
// No codegen dependency is required, the reflection engine comes as a dependency of the `api-dynamic` artifact.
api("com.yandex.yatagan:api-dynamic:${yataganVer}")
```

For **java-only** project:

```kotlin
api("com.yandex.yatagan:api-public:${yataganVer}")
// best codegen backend for Java-only, no need to use kapt/ksp.
annotationProcessor("com.yandex.yatagan:processor-jap:${yataganVer}")
```

**Android** projects are advised to follow the same usage guidelines,
though make sure to read the [notes](#android) on reflection on Android.
An example of a recommended way to use Yatagan for Android projects:

```kotlin
// Use reflection in debug builds.
debugApi("com.yandex.yatagan:api-dynamic:${yataganVer}")

// Use codegen in releases
releaseApi("com.yandex.yatagan:api-public:${yataganVer}")
if (kspEnabled) {
    kspRelease("com.yandex.yatagan:processor-ksp:${yataganVer}")
} else {
    kaptRelease("com.yandex.yatagan:processor-jap:${yataganVer}")
}
```

The `com.yandex.yatagan.Yatagan` entry-point lives in `api-public` and works with both backends:
it always tries the generated implementation first and falls back to reflection if `api-dynamic` is present
on the runtime classpath.

## Backends

### KAPT/APT
APT or KAPT (Yatagan qualifies the artifacts with `jap`, **j**ava **a**nnotation **p**rocessing) is a legacy backend,
though it's stable and can be reliably used by default.

### KSP support

Note, that Yatagan operates in terms of Java type system
and is very sensitive to type equality. In Kotlin, `Collection` and `MutableCollection` are different types, though in
Java it's the same type. From the other hand, Kotlin's `Int` is represented in Java as `int` and `Integer`.
Choosing Java types to maintain semantic compatibility with Dagger, Yatagan converts Kotlin types into Java ones.
KSP API related to JVM is explicitly marked as `@KspExperimental`, and practice shows KSP support for modeling Java
code is at least inconsistent.

Thus, KSP works best for Kotlin-only projects, or projects whose DI-code is mostly Kotlin.
Additional care should be taken with Java projects.

### Reflection support

Reflection support is considered stable in Yatagan. There's already a very similar project for the vanilla Dagger -
[dagger-reflect][DR]. However, Reflection mode in Yatagan has fist-class support and guaranteed to behave the same way,
as generated implementation would.
If a new feature is implemented in Yatagan, reflection automatically works with it.

Technically, reflection mode can be used in production, though it's advised not to do so, as code generation naturally
produces much more performant code. Also, reflection mode is broken by code minifiers, such as Proguard or R8.

Read more in [reflection backend specific notes](rt/README.md).

#### Android

Reflection backend fully supports Android applications starting with `minSdk = 24`.
Below that, static methods in interfaces are not directly supported in Android and have to be "desugared" by AGP.
Yatagan Reflection doesn't currently read such desugared methods as they have no stable ABI and reading them will bring
performance penalties.
So consider using `minSdk = 24` at least for debug build type to safely use Yatagan with Reflection.

## Yatagan vs Dagger API reference

| Dagger2 API (`dagger.**`)              | Status in Yatagan | Notes                                         |
|----------------------------------------|-------------------|-----------------------------------------------|
| `@Component`                           | 🟢 as is          |                                               |
| `@Component.Builder`                   | 🟢 as is          | supports factory method as well               |
| `@Component.Factory`                   | 🟡 converged      | functionality merged into `@Builder`          |
| `@Subcomponent`                        | 🟡 converged      | replaced by `Component(isRoot = false)`       |
| `@Subcomponent.{Builder/Factory}`      | 🟡 converged      | replaced by `Component.Builder`               |
| `Lazy`                                 | 🟢 as is          | now also extends `javax.inject.Provider`      |
| `@Module`                              | 🟢 as is          |                                               |
| `@Binds`                               | 🟡 tweaked        | can bind zero/multiple alternatives           |
| `@BindsInstance`                       | 🟢 as is          |                                               |
| `@Provides`                            | 🟢 as is          | conditionals via separate `@Conditional`      |
| `@BindsOptionalOf`                     | 🟡 replaced       | replaced with [Variants API](#added-apis)     |
| `@Reusable`                            | 🟢 as is          |                                               |
| `MembersInjector`                      | 🔴 unsupported    |                                               |
| `@MapKey`                              | 🟡 renamed*       | `IntoMap.Key`, *`unwrap=false` is unsupported |
| `@multibindings.IntoSet`               | 🟢 as is          |                                               |
| `@multibindings.ElementsIntoSet`       | 🟡 converged      | `IntoSet(flatten = true)`                     |
| `@multibindings.Multibinds`            | 🟢 as is          |                                               |
| `@multibindings.IntoMap`               | 🟢 as is          |                                               |
| `@multibindings.{Int,Class,String}Key` | 🟢 as is          |                                               |
| `@multibindings.LongKey`               | 🔴 removed        | can be declared manually if required          |
| `assisted.*`                           | 🟢 as is          |                                               |
| `producers.*`                          | 🔴 unsupported    |                                               |
| `android.*`                            | 🔴 unsupported    |                                               |
| `grpc.*`                               | 🔴 unsupported    |                                               |
| `hilt.**`                              | 🔴 unsupported    |                                               |
| `spi.*`                                | 🟡 replaced       | Yatagan has its own model for [SPI](#plugins) |

Other behavioral changes:

- `@Binds` can't be scoped (scope rebind is not allowed). Use scope on the implementation.
  Also, Yatagan supports declaring multiple scopes on bindings, 
  so the binding is compatible with _every_ scope declared. Dagger only allowed doing so for components.

- Yatagan requires components, builders, assisted inject factories to be declared as interfaces.
  Abstract classes are forbidden. This is due to the limitations of RT mode. Dagger-reflect has the same limitation.

- If codegen is used, generated component implementations are not named `Dagger<component-name>`
  (`YataganMyComponent` is generated for `MyComponent`), and the access should be made via
  `Yatagan.builder()`/`Yatagan.create()` entry-point invocations.
  This is made to support reflection backend.
  The `com.yandex.yatagan.Yatagan` entry-point is provided by the `com.yandex.yatagan:api-public` artifact
  and works with both backends.
  In [Dagger compatibility mode](#dagger-compatibility-mode-experimental) `Dagger<component-name>` facades
  are generated as well.

- Yatagan does not support `@Nullable` provisions. If a binding returns `null`, or a `@BindsInstance` is supplied with
  `null`, an error will be thrown at run-time. Currently, no compile-time validation is done in the matter.

- Automatic generation of a _typed_ component builder is not supported. Components without an explicit
  `@Component.Builder` can still be created via `Yatagan.autoBuilder()`/`Yatagan.create()` entry-points,
  though the auto-builder API is not type-safe - declare an explicit builder where possible.

- Member inject in Kotlin code should be used with care:
  `@Inject lateinit var prop: SomeClass` will work as expected,
  though `@Inject @Named("id") lateinit var prop: SomeClass` will not - qualifier annotation will go to the *property*
  instead of *field*, and Yatagan will not be able to see it.
  In fact vanilla Dagger will also fail to see it in some scenarios, though it tries to do so on the best-effort basis.
  Yatagan can't read annotations from Kotlin properties, so the following explicit forms should be used instead:
  `@Inject @field:Named("id") lateinit var prop: SomeClass` to inject directly to the field, or
  `@set:Inject @set:Named("id") lateinit var prop: SomeClass` to inject via setter.

Yatagan was written from scratch, and as major known inconsistencies are documented here,
there is a possibility for differences that are overlooked.
If you happen to discover one, please report it.

## Migration from Dagger

The easiest way to try Yatagan on a Dagger project is the experimental
[Dagger compatibility mode](#dagger-compatibility-mode-experimental) — it requires no source changes at all.
The full migration described below removes the Dagger dependency entirely.

Strictly speaking, Yatagan and Dagger are _not directly compatible_.
Yatagan uses a separate binary-incompatible set of annotations and helpers
to give it a degree of freedom to extend and enhance the API.

Yet for the majority of cases, as documented in the [api reference][REF],
annotations and classes differ only in package names, which makes migration from Dagger to Yatagan somewhat trivial.

The general idea of steps one needs to take to migrate from Yatagan to Dagger:

1. Replace `import dagger\.multibindings\.` -> `import com.yandex.yatagan.`
2. Replace `import dagger\.assisted\.` -> `import com.yandex.yatagan.`
3. Replace `import dagger\.` -> `import com.yandex.yatagan.`
4. Replace `@Subcomponent` annotations with `@Component(isRoot = false)` ones.
5. Replace `@Component.Factory` with `@Component.Builder`.
6. Get rid of all nullable provisions. Yatagan does not support them.
7. Replace `DaggerMyComponent.builder()` with `Yatagan.builder(MyComponent.Builder::class.java)`,
   and `DaggerMyComponent.create()` with `Yatagan.create(MyComponent::class.java)` or
   `Yatagan.autoBuilder(MyComponent::class.java)` for components without an explicit builder.
8. Mark all components, that are accessed from multiple threads as `@Component(.., multiThreadAccess = true)`.
   If you are unsure, if a component is accessed from a single thread, but ideally it should be,
   you can set up a check with the `yatagan.threadCheckerClassName` processor option
   (see [Options](#options)).
9. Run build and fix all remaining inconsistencies (like implicitly included subcomponents, etc..).

## Dagger compatibility mode (experimental)

Yatagan has an experimental mode, enabled with the
`yatagan.experimental.enableDaggerCompatibility` processor option, in which:

- Dagger annotations and types with supported functionality (`@dagger.Component`, `@dagger.Module`,
  `@dagger.Provides`, `dagger.Lazy`, ...) are recognized alongside Yatagan ones,
  and may be mixed in the same codebase;
- a `Dagger<ComponentName>` facade class is generated for **every** root component —
  Dagger- and Yatagan-annotated alike (e.g. `DaggerMyComponent.create()`,
  `DaggerMyComponent.builder()`/`.factory()`; nested components use `_`: `DaggerOuter_Inner`),
  so existing Dagger call sites keep compiling.

Hilt, `dagger.android`, Dagger Producers, Dagger-gRPC and Dagger SPI plugins remain unsupported.

For the reflection backend, use the `enableDaggerCompatibility` property in `parameters.properties`
(see [reflection notes](rt/README.md#reflection-specific-api)); this additionally requires the Dagger API
jar on the runtime classpath.

The feature is experimental — validate your graph thoroughly before shipping.

## Added APIs

Yatagan introduces the following new APIs, that can be utilized to work with **conditional bindings**

The first one is
[`@ConditionExpression`](api/public/src/main/kotlin/com/yandex/yatagan/ConditionExpression.kt).
With this annotation, one can declare a **runtime condition** that can be evaluated and its value will
determine the presence/absence of a binding under the condition.

To put a binding under a given condition, one must use
[`@Conditional`](api/public/src/main/kotlin/com/yandex/yatagan/Conditional.kt)
annotation on a binding or a class with `@Inject`-annotated constructor.

Variant API ideally replaces Dagger's `@BindsOptionalOf` and makes it more powerful.
It's very alike to how Android works with flavors and dimensions, only here we can declare components having
such flavors and include/exclude bindings based on them.
To use that, one can employ `@Conditional(.., onlyIn = ...)` and `@Component(variant = ...)` attributes. 

Feel free to read a small tutorial doc, that includes [how to use conditions and variants](api/README.md).

## Plugins

One can write an extension for _validation_ pipeline for Yatagan to implement one's custom graph inspections.
No additional code generation is currently supported for plugins, and they can not modify graphs under inspection.
This works, as for Dagger, via SPI. Read more [here](validation/spi/README.md).

## Options

Yatagan has some options, that tweak its behavior. They are provided as normal annotation processor options.
However, reflection backend requires a different approach in specifying them,
as documented [here](rt/README.md#reflection-specific-api).

| Option key                                       | Default value | Description                                                                                    |
|--------------------------------------------------|---------------|------------------------------------------------------------------------------------------------|
| `yatagan.enableStrictMode`                       | true          | if enabled, every _mandatory warning_ is reported as an error                                  |
| `yatagan.maxIssueEncounterPaths`                 | 5             | the max number of places `Encountered in` in an error message to be mentioned                  |
| `yatagan.usePlainOutput`                         | false         | if enabled, reporting is done in plain text, without ANSI coloring                             |
| `yatagan.threadCheckerClassName`                 | —             | FQN of a class with a static parameterless `assertThreadAccess` method, used for thread checks |
| `yatagan.experimental.enableDaggerCompatibility` | false         | enables [Dagger compatibility mode](#dagger-compatibility-mode-experimental)                   |

[D2]: https://dagger.dev/
[KSP]: https://kotlinlang.org/docs/ksp-quickstart.html
[DR]: https://github.com/JakeWharton/dagger-reflect
[REF]: #yatagan-vs-dagger-api-reference
[MEDIUM]: https://medium.com/p/eb58ca20d52f/