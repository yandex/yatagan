# Dagger Lite

{{TOC}}

## Summary

Dagger Lite (**DL** in docs) is a **Dependency Injection framework** based on Google Dagger2.
DL's API is based on that of Google Dagger2 (D2 further in docs), fully mimicking it in some parts.

Additionally, DL extends its base API with Conditions and Variants APIs.
Read <a href="#AdditionalAPIs">this section</a> for more info on that.

DL can work in multiple modes (use different "backends"):

- Code generation
    - APT/KAPT
    - KSP _(experimental, unstable due to KSP utter inconvenience to use for Java code generation)_
- Reflection _(experimental)_

## DL and D2

### Benefits

#### Build speed (the origin of "Lite")

The most significant optimization point for DL is **build speed.**
Measurements show build time decrease by at least 50% for kapt backend for a large multi-module mixed-language project,
though numbers may, of course, vary across projects and D2 usage patterns.

This is achieved mostly due to the fact, that DL doesn't generate a factory class for every `@Inject` constructor and
[@Provides][com.yandex.yatagan/Provides@:api] binding.
Hence, no need to run DL on a project module if there's no root [@Component][com.yandex.yatagan/Component@:api]
there.
Also, those factories needed to be generated and compiled, which by itself introduces build time overhead.

[Kotlin Symbol Processing](https://kotlinlang.org/docs/ksp-overview.html)
(KSP) can reduce build times even further without resorting to reflection.
**DL supports KSP in experimental mode**, while D2 still does not.
KSP backend for now has issues with mixed Java/Kotlin projects, mostly due to immaturity of KSP itself.
So caution is advised for early adopters. Experience shows, that KSP works more stably on pure-Kotlin projects.
For additional safety, it may be prudent to use KSP for local development, and kapt for release builds on CI,
if possible.

#### "Lite" validation

DL implements validation in a reliable yet strictly on-demand basis. Entities are validated if and only if they are
used in code generation. Unused entities are not validated.

DL also aims to make error messages less verbose while retaining necessary information. This is achieved by grouping
similar error messages and listing places where they occur.

#### Runtime performance

DL may slightly improve runtime performance for graphs, that are known to be used only from a single thread.
For graphs, that are used from multiple threads, one should explicitly declare it:
[@Component(multiThreadAccess = true)][com.yandex.yatagan/Component#multiThreadAccess@:api]
Also, some other small optimizations are made and there are plans to keep researching for more opportunities.
See the corresponding [issues](https://st.yandex-team.ru/issues/?queue=%22DAGGERLITE%22&tags=%22RuntimeSpeed%22).

#### Reflection for local development

Furthermore, DL provides **first-class reflection backend (RT) support**.
This is designed to further reduce build times by fully eliminating expensive codegen step.
DL dependency graphs will be built at application runtime on demand.
Depending on graph size and complexity, application startup/responsiveness time may decrease,
so RT is advised to be used only during local development.
Release builds should always use codegen for maximum runtime performance and full compile-time validation.

Read more on RT in the [:api-dynamic] doc.

### Compatibility & Migration

Strictly speaking, DL and D2 are _not directly compatible_.
DL uses a separate binary-incompatible set of annotations and helpers
to give it a degree of freedom to extend and enhance the API.

Yet for the majority of cases, annotations and classes differ only in package names,
which makes migration from D2 to DL somewhat trivial.

For full API compatibility consult the [:api] doc.

The general idea of steps one needs to take to migrate from DL to D2:

1. Replace `import dagger\.multibindings\.` -> `import com.yandex.yatagan.`
2. Replace `import dagger\.assisted\.` -> `import com.yandex.yatagan.`
3. Replace `import dagger\.` -> `import com.yandex.yatagan.`
4. Replace `@Subcomponent` annotations with `@Component(isRoot = false)` ones.
5. Replace `@Component.Factory` with `@Component.Builder`.
6. Run build and fix all remaining inconsistencies (like implicitly included subcomponents, etc..).
7. Get rid of all nullable provisions. DL does not support them.
8. Mark all components, that are accessed from multiple threads as
   [@Component(multiThreadAccess = true)][com.yandex.yatagan/Component#multiThreadAccess@:api].
   If you are unsure, if a component is accessed from a single thread, but ideally it should be,
   you can set up [ThreadAssertions][com.yandex.yatagan/ThreadAssertions@:api].

DL was written from scratch, and as major known inconsistencies are documented in the [:api] doc,
there is a possibility for differences that are overlooked.
If you happen to discover one, please report it.

## Usage (Gradle)

Codegen dependency is only required for modules, that contain root component declarations.

For java-only project:

```kotlin
dependencies {
    implementation("com.yandex.yatagan:api-compiled:%%version%%")
    // best codegen backend for Java-only, no need to use kapt/ksp.
    annotationProcessor("com.yandex.yatagan:processor-jap:%%version%%")
}
```

For kotlin-only/mixed project using `kapt`:

```kotlin
// Ensure `kotlin-kapt` plugin is applied
dependencies {
    implementation("com.yandex.yatagan:api-compiled:%%version%%")
    // kapt is slow but generally reliable for mixed projects.
    kapt("com.yandex.yatagan:processor-jap:%%version%%")
}
```

For kotlin-only/mixed project using `KSP`:
([How to](https://kotlinlang.org/docs/ksp-quickstart.html#use-your-own-processor-in-a-project) apply KSP plugin)

```kotlin
// Ensure `com.google.devtools.ksp` plugin is applied
dependencies {
    implementation("com.yandex.yatagan:api-compiled:%%version%%")
    // KSP implementation is unstable. Works best for pure-Kotlin projects.
    ksp("com.yandex.yatagan:processor-jap:%%version%%")
}
```

To speed up build (most relevant for `kapt` users) one can replace codegen with runtime reflection:

```kotlin
dependencies {
    implementation("com.yandex.yatagan:api-dynamic:%%version%%")
    // No codegen dependency is required, the reflection engine comes as a dependency of the `api-dynamic` artifact.
}
```

## Additional APIs

DL has some extensions to the vanilla D2 API:

**Condition API** (see [@Conditional][com.yandex.yatagan/Conditional@:api]),
and **Variant API** (see [@Conditional.onlyIn][com.yandex.yatagan/Conditional#onlyIn@:api]).

_NOTE:_ Usage of these APIs often leads to questionable architectural solutions;
All these APIs were introduced to solve SuperApp-specific needs due to its pre-Dagger history.
These APIs are planned to be marked as "incubating" in 1.0.0,
so it's advised for clients to use other techniques if possible.

The general advice for replacing Condition API is:
use an interface and its real and stub implementations via `@Provides` instead of conditional binding.
This way the API is clean and there's no need for clients to know about conditional bindings and use `Optional`.

These APIs usage is justified if your project *heavily* uses runtime conditions (e. g. for AB experiments)
and/or builds different applications from a single codebase, that partially share Dagger graphs.

## Other similar third-party solutions

1. [Anvil](https://github.com/square/anvil). It doesn't support Java at all.
   As for pure-Kotlin projects, DL on KSP is expected to perform much better, as Anvil only generates
   factory-classes as component generation is still done by D2 using slow kapt.
2. [dagger-reflect](https://github.com/JakeWharton/dagger-reflect). Nice project,
   which performs better at runtime, than DL's RT mode (for now).
   Yet RT for DL is a first-class citizen, not a third-party project;
   DL is designed in a way that makes codegen and reflection behave identically (to a reasonable extent),
   by sharing as much code as possible between codegen and RT.
   So any new feature implemented for DL is automatically available for all
   supported backends with very little backend-specific code.
   See the corresponding research [issue](https://st.yandex-team.ru/DAGGERLITE-34) for DL.

## Regarding KSP support status

As mentioned in sections above, KSP has experimental support in DL,
so it is not advised to be used in production builds.
Bug reports are welcome.

KSP is tightly tied to a specific Kotlin compiler version and does not support _any other_ version.
It is yet planned to research how to provide support for multiple Kotlin versions.

Current **Kotlin version** for KSP: **%%kotlin_version%%**

## Processor options

- `daggerlite.enableStrictMode` (enabled by default) can be turned off for the time of the migration from D2 to
 turn some non-blocker errors into warnings.

## Useful links

- Code: [repository](%%repo_link%%)
- Startrek queue: [DAGGERLITE](https://st.yandex-team.ru/DAGERLITE)
- Dagger2 [docs](https://dagger.dev/dev-guide/)
- Old [slides](https://nda.ya.ru/t/DPG9wglr4sj2v6) about DL in SuperApp
