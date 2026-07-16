# Yatagan KCP backend — support status

Status of the Kotlin Compiler Plugin (KCP) backend as of 2026-07-15, branch `users/bacecek/kcp`.

The KCP backend generates component implementations directly as JVM IR during `kotlinc`
compilation — no KAPT/KSP rounds, no reflection. It reuses the shared Yatagan core
(`lang/api` via `lang/kcp`, `core/model`, `core/graph`, `validation`) and adds:

| Module | Role |
|---|---|
| `lang/kcp` | IR-backed implementation of the Yatagan `lang` model (the "Java view" of Kotlin/Java declarations) |
| `codegen/ir` | `IrComponentEmitter` — emits component implementations as IR into the component's own file |
| `processor/kcp` | `IrGenerationExtension` glue: root discovery, validation, options, SPI plugin loading; published as a self-contained fat jar (`com.yandex.yatagan:processor-kcp`) |
| `gradle-plugin` | `YataganGradleSubplugin` (`KotlinCompilerPluginSupportPlugin`), plugin id `com.yandex.yatagan`; adds `api-public` to the compilation and puts the fat jar on the compiler plugin classpath |

## What works

Everything the corpus covers, at parity with KSP for Kotlin sources:

- Components with creators (`@Component.Builder` both styles), creator-less roots with a real
  `autoBuilder()` implementation, subcomponents (nested impls in the root's impl class).
- All binding kinds: provisions, `@Binds` aliases/alternatives, inject constructors, instance
  and module-instance inputs, component dependencies, member injectors,
  List/Set/Map multibindings (incl. `@IntoMap` with primitive/String/Class/enum keys),
  `Lazy`/`Provider`/`Optional`, scoped caching (`@Volatile` + synchronized DCL slow path),
  `multiThreadAccess`, thread-checker assertions.
- Assisted inject, including **generic assisted factories** (`Factory<T>` used as `Factory<Concrete>`).
- Runtime conditions and variants: eager `boolean` fields + lazy tri-state accessors,
  short-circuit boolean expression chains, cross-graph literal routing, conditional
  multibinding contributions, `AlternativesBinding`, `ConditionExpressionValueBinding`.
- Mixed Java/Kotlin graphs: Java modules, dependencies, inject constructors/members all work
  (Java classes resolve as lazy stubs by name). Java-view fidelity: parameter-position wildcard
  baking, `@JvmSuppressWildcards`/`@JvmWildcard`, raw types, collection-mutability and
  nullability erasure in assignability, `@JvmStatic`/companion hoisting.
- Member injection through property accessors (works cross-file; raw field access only for
  Java fields / `@JvmField`).
- **SPI validation plugins**: `ValidationPluginProvider`s are discovered via `ServiceLoader`
  from the compiler plugin classpath. Consumers add their validators jar to the
  `kotlinCompilerPluginClasspath` configuration (prefer `isTransitive = false` — the fat jar
  already carries all `com.yandex.yatagan` classes). All `-Xplugin` jars share one classloader,
  so discovery needs no extra wiring.
- **Incremental compilation**: the processor records `LookupTracker` lookups from each root
  component file to every class its graph closure reads (class-level PACKAGE-scope lookups +
  member-level CLASSIFIER-scope lookups), plus `ExpectActualTracker` file links for same-module
  classes (covers declarations *added* later, which name-based lookups can't anticipate — the
  same technique Metro uses). Editing a binding anywhere in the graph re-triggers component
  regeneration; verified on browser-ui (breaking an `@Inject` constructor in a file the
  component never references fails the incremental build with a missing-binding error).

## Verification

- Corpus (`:testing:tests`, honest driver: codegen on, compiled runtime, javac step for Java
  sources): **111 [KCP] tests → 86 pass, 0 fail, 25 skipped** (24 Java-declared root
  components — architecturally impossible; 1 file-facade `@Condition` — shared with KSP2).
- DivKit (3 modules): builds on KCP; 8806 unit tests behave identically to KSP.
- browser-ui (435-module production app, heavy conditions/variants usage): full
  `b build -n` green across 4 app targets, ~1760 generated classes; SPI validators active;
  `:alicekit:alicenger` unit tests (Robolectric, real component instantiation) pass.

## Known gaps vs KAPT/KSP

1. **Java-declared root components** — permanent. `IrGenerationExtension` only enumerates
   Kotlin files; Java classes are pull-only stubs. Root `@Component` interfaces must be
   Kotlin; everything else may stay Java. (Metro has the same limitation.)
2. **No generated sources on disk** — IR goes straight to bytecode. Nothing to read or step
   through; corpus runs behavior-only (`checkGoldenOutput = false`).
3. **Generated classes are not referencable from source** — `YataganFoo` doesn't exist at
   resolution time. Only the reflective `Yatagan.builder(...)` / `Yatagan.autoBuilder(...)`
   loader works. (Also one of the reasons dagger-compat can't be supported.)
4. **Dagger-compat mode** — unsupported.
5. **Options plumbing** — the Gradle plugin passes no options; `yatagan.*` options must be
   passed manually as `-P plugin:com.yandex.yatagan:<key>=<value>`. Honored: `enableStrictMode`,
   `usePlainOutput`, `maxIssueEncounterPaths`, `experimental.allConditionsLazy`,
   `experimental.omitProvisionNullChecks`, thread-checker class. Ignored: `maxSlotsPerSwitch`
   (the IR emitter has its own dispatch design).
6. **No source locations on messages** — validation errors print the graph path but are not
   anchored to file:line.
7. **Maturity** — see Verification above; no long-term production mileage yet.

## Consumer setup (as proven on browser-ui)

```kotlin
// settings/build classpath: mavenLocal hosts processor-kcp + gradle-plugin (version 2.0.0)
plugins { id("com.yandex.yatagan") } // or apply YataganGradleSubplugin by class

dependencies {
    // SPI validators (optional):
    add("kotlinCompilerPluginClasspath",
        project(":my-validators").also { /* isTransitive = false */ })
}
```

Republish loop while iterating on the backend:
`GRADLE_USER_HOME=$HOME/.gradle ./gradlew :processor:kcp:publishToMavenLocal`
