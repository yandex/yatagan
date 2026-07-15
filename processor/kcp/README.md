# Yatagan KCP backend

The KCP backend is a Kotlin compiler plugin: it generates component implementations as JVM IR while
`kotlinc` compiles the module, so there is no KAPT/KSP round and no runtime reflection. It reuses the
shared Yatagan core (`core/model`, `core/graph`, `validation`), so the graph model, validation
messages and processor options behave the same as in the other backends.

| Module | Role |
|---|---|
| `lang/kcp` | IR-backed implementation of the Yatagan `lang` model (the "Java view" of Kotlin/Java declarations) |
| `codegen/ir` | emits component implementations as IR into the component's own file |
| `processor/kcp` | `IrGenerationExtension` glue: root discovery, validation, options, SPI plugin loading; published as a self-contained fat jar (`com.yandex.yatagan:processor-kcp`) |
| `gradle-plugin` | `YataganGradleSubplugin`, plugin id `com.yandex.yatagan`; puts the fat jar on the compiler plugin classpath and adds `api-public` to the compilation |

## What works

Everything the shared `:testing:tests` corpus covers, at parity with KSP for Kotlin sources:

- Components with creators (`@Component.Builder`, both builder and factory styles), creator-less
  roots with a real `autoBuilder()` implementation, subcomponents (nested implementations inside the
  root implementation class).
- All binding kinds: provisions, `@Binds` aliases and alternatives, inject constructors, instance and
  module-instance inputs, component dependencies, member injectors, List/Set/Map multibindings
  (including `@IntoMap` with primitive, `String`, `Class` and enum keys), `Lazy`/`Provider`/`Optional`,
  scoped caching (`@Volatile` field plus synchronized double-checked locking), `multiThreadAccess`
  and thread-checker assertions.
- Assisted inject, including generic assisted factories (`Factory<T>` used as `Factory<Concrete>`).
- Runtime conditions and variants: eager `boolean` fields plus lazy tri-state accessors,
  short-circuit boolean expression chains, cross-graph literal routing, conditional multibinding
  contributions, binding alternatives and condition expression values.
- Mixed Java/Kotlin graphs: Java modules, dependencies, inject constructors and injected members all
  work (Java classes resolve as lazy stubs by name). Java-view fidelity covers parameter-position
  wildcard baking, `@JvmSuppressWildcards`/`@JvmWildcard`, raw types, collection-mutability and
  nullability erasure in assignability, `@JvmStatic` and companion hoisting.
- Member injection through property accessors; raw field access is used only for Java fields and
  `@JvmField` properties.
- [SPI validation plugins](../../validation/spi/README.md): providers are discovered with
  `ServiceLoader` from the compiler plugin classpath. All `-Xplugin` jars share one classloader, so
  putting the validators jar next to the processor is all the wiring needed.
- Incremental compilation: the processor records `LookupTracker` lookups from each root component
  file to every class its graph closure reads (class-level `PACKAGE`-scope lookups plus
  member-level `CLASSIFIER`-scope lookups), plus `ExpectActualTracker` file links for same-module
  classes, which also covers declarations added later. Editing a binding anywhere in the graph
  re-triggers component regeneration.

## Known gaps vs KAPT/KSP

1. **Java-declared root components** — permanent. `IrGenerationExtension` only enumerates Kotlin
   files; Java classes are pull-only stubs. Root `@Component` interfaces must be Kotlin, everything
   else may stay Java.
2. **No generated sources on disk** — IR goes straight to bytecode. There is nothing to read or step
   through, and the corpus checks behavior only (no golden output comparison).
3. **Generated classes are not referencable from source** — `YataganFoo` does not exist at
   resolution time, so only the reflective `Yatagan.builder(...)`/`Yatagan.autoBuilder(...)`
   entry-points work.
4. **Dagger compatibility mode** — unsupported.
5. **Options plumbing** — the Gradle plugin passes no options of its own; `yatagan.*` options are
   passed as compiler plugin options (see below). Honored: `yatagan.enableStrictMode`,
   `yatagan.usePlainOutput`, `yatagan.maxIssueEncounterPaths`, `yatagan.threadCheckerClassName`,
   `yatagan.experimental.allConditionsLazy`, `yatagan.experimental.omitProvisionNullChecks`.
   Ignored: `yatagan.experimental.maxSlotsPerSwitch` (the IR emitter has its own dispatch design) and
   `yatagan.experimental.enableDaggerCompatibility` (see gap 4).
6. **No source locations on messages** — validation errors print the graph path, but are not anchored
   to file:line.
7. **Compiler version coupling** — `processor-kcp` is built against the Kotlin compiler API of the
   `kotlin` version pinned in `gradle/libs.versions.toml` and has to be used with a matching
   `kotlinc`.

## Consumer setup

```kotlin
plugins {
    kotlin("jvm")
    id("com.yandex.yatagan") version yataganVer
}
```

The plugin adds `com.yandex.yatagan:api-public` to the compilation and `com.yandex.yatagan:processor-kcp`
to the compiler plugin classpath. As with the other backends, only modules declaring root components
need it.

SPI validators go into the `kotlinCompilerPluginClasspath` configuration; a non-transitive dependency
is enough, because the fat jar already carries every `com.yandex.yatagan` class:

```kotlin
dependencies {
    "kotlinCompilerPluginClasspath"("com.example:my-yatagan-plugins:1.0") {
        isTransitive = false
    }
}
```

Processor options are passed as compiler plugin options:

```kotlin
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions.freeCompilerArgs.addAll(
        "-P", "plugin:com.yandex.yatagan:yatagan.usePlainOutput=true",
    )
}
```

### Validation without code generation

```text
-P plugin:com.yandex.yatagan:yatagan.kcp.codegen=false
```

The plugin then builds and validates the graph but emits nothing, so such modules need `:api:dynamic`
(the reflection backend) at runtime instead of `:api:public`.

## Wiring inside this repository

The published Gradle plugin resolves `processor-kcp` from a repository, so modules in this repo pass
the locally built fat jar to `kotlinc` directly. `:testing:kcp-consumer` does it like this:

```kotlin
import org.gradle.api.tasks.ClasspathNormalizer
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val kcpPlugin = configurations.register("kcpPlugin")

dependencies {
    implementation(project(":api:public"))
    kcpPlugin(project(":processor:kcp"))
}

tasks.withType<KotlinJvmCompile>().configureEach {
    dependsOn(kcpPlugin)
    inputs.files(kcpPlugin).withNormalizer(ClasspathNormalizer::class.java)
    compilerOptions.freeCompilerArgs.addAll(kcpPlugin.map { classpath ->
        listOf("-Xplugin=${classpath.joinToString(",") { it.absolutePath }}")
    })
}
```

## Running the tests

```shell
./gradlew :lang:kcp:test :codegen:ir:test :processor:kcp:test
./gradlew :testing:kcp-consumer:test
./gradlew :testing:tests:test
```

The module tests invoke the pinned Kotlin compiler on small sources and assert on the compiled
result. `:testing:kcp-consumer` is a normal Gradle consumer built through native code generation.
The shared corpus runs KCP in a separate compiler process, because Room compile-testing embeds a
different Kotlin version; cases KCP cannot support (Java-declared root components, or graphs the
emitter reports as `Unsupported KCP component`) are skipped rather than silently routed through
another backend.
