---
name: yatagan-2-migration
description: Use when upgrading a project from Yatagan 1.x to Yatagan 2.0 — bumping the dependency version, or hitting 2.0 errors such as "Missing binding" for a child component, @Provides(Conditional(...)) not compiling, setupReflectionBackend/setThreadAsserter missing, or Yatagan$ generated class names not found.
---

# Yatagan 1.x → 2.0 Migration

Migrate a codebase from Yatagan 1.x (typically 1.6.2) to 2.0. Work through every step below; each is independent — skip a step only after its search (Step 0 inventory) proves the project has nothing to migrate for it.

## Ground rules

- **Full cleanup.** Deprecated-but-still-working Yatagan APIs (legacy `@Condition` family, `api-compiled` artifact) MUST be migrated too. Only warnings unrelated to Yatagan are out of scope.
- **Redirect search output to files.** For every `grep`/`rg`, redirect raw output to a file and read the file (e.g. `rg <pattern> > /tmp/yatagan-scan.txt 2>&1`). Inline tool output may be truncated or rewritten by context-saving wrappers, producing wrong match counts. On very large repos `rg` can time out or silently produce an empty output file — if a search is slow or a result file is suspiciously empty, fall back to plain `grep -r --include=... --exclude-dir=build` and cross-check.
- **Distrust zero hits — and huge hit counts.** If a search returns nothing, re-run with a simpler substring and different quoting before concluding the pattern is absent. In particular, mind `$` escaping per tool: `grep -F 'Yatagan$'` (literal, no escape) or `grep -E 'Yatagan\$'` (escaped) — `-F` with `\$` matches a literal backslash and silently finds nothing. Conversely, exclude `build/`, `.gradle/`, and other generated-output directories, or generated sources will drown the real matches.
- **Compiling is not evidence of cleanup.** Yatagan 2.0 still ships the deprecated *public* symbols (`@Condition` family, `Yatagan.setThreadAsserter`, the `ThreadAsserter` type, `api-compiled`), so a green build does not prove steps 2b/2d/2i were done — only a zero-hit re-scan does. Internal machinery behind them may be gone, though (e.g. `com.yandex.yatagan.internal.ThreadAssertions.setAsserter` no longer exists) — profile/keep-rule entries naming internals are dead references to delete.
- **Build per repo rules.** Find how this repository verifies a build (its own docs / agent rules — and check for project-specific CLI wrappers on `PATH`, not just Gradle/Maven invocations named in docs) and use that command for the baseline and final verification. A transient toolchain or build-daemon crash on a first/cold build is environmental — retry once before treating it as a migration failure.

## Step 0 — Recon and baseline

1. Locate the Yatagan version declaration (version catalog, dependency manifest, or build scripts) and all Yatagan artifact dependencies (`api-public`, `api-compiled`, `api-dynamic`, `api-common`, processor artifacts).
2. Run the repo's build check to confirm a green baseline **before** changing anything.
3. Inventory usages (redirect each to a file, then read the file):
   - `@Provides(` — conditional provides (step 2a)
   - `@Condition(`, `@AllConditions(`, `@AnyCondition(`, `@AnyConditions(` — legacy conditions (step 2b). Use these paren-anchored literals (with `grep -F`), not `\b` word boundaries — `\b` is a GNU extension that BSD grep may silently ignore. Also search for stale imports with an **end-anchored** pattern — `grep -E 'yatagan\.(Condition|AllConditions|AnyCondition|AnyConditions)$'` — or the search drowns in `Conditional`/`Conditionals`/`ConditionExpression`, which share the prefix. Beware the near-miss **`@Conditionals`** (repeatable container of `@Conditional`): it looks like the legacy `@AnyConditions` but is a current API and is NOT legacy — exclude it from 2b hits.
   - `isRoot = false` / component interfaces — child components (step 2c; triage per 2c, do not audit every injection site up front)
   - `ThreadAsserter` (the type — this also catches baseline-profile/keep-rule entries like `Lcom/yandex/yatagan/ThreadAsserter;` and `ThreadAssertions;->setAsserter(...)` that a `setThreadAsserter` search misses), plus `setThreadAsserter` and `yatagan.experimental.omitThreadChecks` (step 2d)
   - `setupReflectionBackend`, `resetReflectionBackend`, `useCompiledImplementationIfAvailable` (step 2e)
   - `DynamicValidationDelegate` implementations (step 2f)
   - SPI usage: `ValidationPluginProvider`, `lang-api`, `core-model-api`, `core-graph-api`, `validation-api`, `validation-spi` (step 2g)
   - `Yatagan\$` in ProGuard/R8 rules, `Class.forName`, baseline profiles, serialized names, tooling (step 2h)
   - `api-common`, `api-compiled` dependencies (step 2i)
   - `yatagan.experimental.reportDuplicateAliasesAsErrors` processor option (step 2j)
   - **Build logic**: `buildSrc`/`build-logic`/convention plugins that set `yatagan.*` processor options, construct generated class names (string concatenation with `Yatagan`), or register bytecode transforms. Build tooling that hard-codes the 1.x naming scheme is the highest-risk find — and it may sit behind a default-off build flag, so a green default build proves nothing about it (see Step 3). Record the **names and invocation syntax** of any build flags gating Yatagan-related code paths (e.g. `-Pflag=true`, env vars) — Step 3 needs them to verify coverage.

## Step 1 — Bump the version

Set the Yatagan version to `2.0.0` wherever it is declared.

## Step 2 — Code migrations

### 2a. `@Provides` no longer accepts `Conditional` arguments

`@Provides` is a plain marker in 2.0. Move each `Conditional(...)` argument out into a separate `@Conditional(...)` annotation on the same method:

```kotlin
// 1.x
@Provides(Conditional(MyFeature::class))
fun provideImpl(): MyApi = MyApiImpl()

// 2.0
@Provides
@Conditional(MyFeature::class)
fun provideImpl(): MyApi = MyApiImpl()
```

Watch for all argument forms: positional `@Provides(Conditional(A::class))`, multiple `@Provides(Conditional(A::class), Conditional(B::class))` (becomes repeated `@Conditional` annotations), and named/array `@Provides(value = [Conditional(A::class)])` (Java uses `{...}`). Inside `@Provides(...)` you may also find a `Conditionals(...)` container argument — handle it here; this is unrelated to the *class-level* `@Conditionals` annotation, which is unchanged and must not be touched. Arguments may span multiple lines — rewrite by matching parentheses, not by line.

Formatting after the rewrite — check **every** rewritten site against the project's per-language line-length limits (`.editorconfig` limits often differ between Java and Kotlin):

- extraction lengthens lines even without collapsing (the added `@` can push a previously-legal line over the limit);
- a hoisted multi-line argument was nested one level deeper inside `@Provides(...)` — dedent its continuation lines and closing parenthesis by one level.

`onlyIn = [...]` and repeated `@Conditional` work the same on methods as on classes — in 2.0 `@Conditional` is repeatable (`@JvmRepeatable(Conditionals::class)`) and targets functions and property getters, so the repeated-annotation form is legal in both Kotlin and Java. `@Conditional` on classes is unchanged — no migration.

### 2b. Legacy conditions → `@ConditionExpression`

`@Condition`, `@AllConditions`, `@AnyCondition`, `@AnyConditions` are deprecated. Replace with `@ConditionExpression`:

```kotlin
// legacy
@Condition(Features::class, condition = "isAliceProEnabled")
annotation class AliceProEnabled

// modern
@ConditionExpression("isAliceProEnabled", Features::class)
annotation class AliceProEnabled
```

`@ConditionExpression` supports full boolean expressions — `&`, `|`, `!`, feature references (`@OtherFeature`), multiple imports with optional `importAs` aliases — so repeated `@Condition`/`@AnyCondition` stacking (CNF) collapses into one expression:

```kotlin
// legacy
@AnyCondition(
    Condition(Features::class, "isA"),
    Condition(Features::class, "isB"),
)
annotation class AOrB

// modern
@ConditionExpression("isA | isB", Features::class)
annotation class AOrB
```

The legacy `condition` string is already valid `@ConditionExpression` syntax, including the `!` negation prefix and dotted access paths — **copy it verbatim**; do not re-derive getter/`INSTANCE` spellings that were already correct in 1.x. (For *new* expressions: access paths use Java-view names — property getters as `getFoo`, Kotlin objects via `INSTANCE`, companions via `Companion`.)

Reference syntax rules: the **short (unqualified) form** (`"isAliceProEnabled"`) is legal only when the annotation has **exactly one import**; with two or more imports every variable must use the **qualified form** `Cls::path` (or an `importAs` alias). Since a legacy `@Condition` only ever named one class, every mechanically-converted site has exactly one import — **prefer the short positional form** `@ConditionExpression("expr", X::class)`: it is legal there and length-neutral with the legacy annotation, whereas the qualified form (`"X::expr"`) and the named form (`imports = [X::class]`) each add characters that can push hundreds of single-line sites over the limit. Decide the form (and check the project's per-language line-length limits, as in 2a) **before** rewriting a large batch. Non-static providers are resolved from the graph, same as 1.x. `@ConditionExpression` exists in 1.6.2 with the same syntax, so this step can't break a 1.x build.

### 2c. Direct injection of child components is forbidden

Injecting a child (non-root) component *instance* into parent-graph bindings no longer works — it produces `Missing binding for ...` with the note *"A dependency seems to be a child component, try injecting its factory instead."*

**Triage first — most components need no work.** For each non-root component, check whether it declares its own `@Component.Builder`/`@Component.Factory`: those are already in the 2.0 shape, skip them. Audit only:

1. children **without** an explicit builder/factory;
2. parent-side abstract methods **returning** a child component type (now factory methods — see below);
3. bindings or `@Inject` constructors taking a child component type (including `Lazy<Child>`/`Provider<Child>`) as a parameter.

Expect categories 2 and 3 to be dominated by false positives; dismissal rules:

- methods returning `Child.Builder`/`Child.Factory` are **already the 2.0 shape** — a builder return type is not a component return type;
- `@BindsInstance` parameters of a child component type are legal (the instance is supplied externally, nothing is missing);
- `@Binds` methods inside the child's **own** modules — a component binding itself into its own graph is still legal;
- plain non-DI code that merely mentions the type (tests, mocks, utilities).

Keep this audit at grep level — do not deep-inspect thousands of candidates. Step 3's build is the backstop: any missed site fails loudly with `Missing binding ... seems to be a child component`. For real direct-injection sites, the fix is to inject the child's builder and create the instance lazily:

```kotlin
// 2.0
@Component(isRoot = false)
interface SettingsComponent {
    /* entry points */
    @Component.Builder
    interface Builder { fun create(): SettingsComponent }
}

class SettingsRouter @Inject constructor(
    private val settingsBuilder: SettingsComponent.Builder,
) {
    private val settings by lazy { settingsBuilder.create() }
}
```

Also: **every abstract method of a component interface that returns a non-root component is now a factory method** (in 1.x, parameterless ones were entry points). A child must expose *either* an explicit `@Component.Builder` *or* factory methods in its parent — both together is an error ("Child components can't have a factory methods declared for them in their parents…"). If the child has an explicit builder, remove parent-side factory methods (or drop the builder).

### 2d. `Yatagan.setThreadAsserter()` → build-time thread checker

`setThreadAsserter(...)` is a deprecated (`ERROR` level) no-op. Replace each asserter with a checker class and a processor option:

```kotlin
object MainThreadChecker {
    @JvmStatic
    fun assertThreadAccess() {
        // port the OLD asserter's body here verbatim
    }
}
```

```kotlin
// build script — KAPT: kapt { arguments { arg(...) } }, KSP: ksp { arg(...) }
arg("yatagan.threadCheckerClassName", "com.example.MainThreadChecker")
```

**Port the old asserter's body — don't substitute a plain `check()`/`throw`.** Projects often route assertions through a suppressible/reportable assert facility; replacing that with an unconditional `check()` changes release-build behavior.

**Placement in multi-module builds:** the option applies in *every* module where the Yatagan processor runs (e.g. all modules applying a convention plugin), so the checker class must be on the **compile classpath of every one of them**. Reusing an existing utility class usually fails this — the symptom is a processor error `Invalid value: Unable to find class ...` in some unrelated module. The robust shape: put the checker in a small dependency-free module (or one with minimal deps) and add it as a dependency in the same place the option is set — as a normal compile+runtime configuration (`implementation`), not `compileOnly`, because reflection mode needs the class at runtime too. The checker itself may equally be a plain Java class with a `public static void assertThreadAccess()` — a Kotlin `object` with `@JvmStatic` is just one option; match the language of the code you're porting. If the repo enforces module-visibility / allowed-dependency-path rules, whitelist the checker module there — a duplicate checker per enforced subtree does not work (same FQN would collide, and the option takes exactly one class name). Requirements (compile-time validated): method named `assertThreadAccess`, `public`/`internal`, static or in a Kotlin `object`, parameterless.

**Reflection mode:** the old `setThreadAsserter` was a runtime global, so it applied under every backend. If the project can build with the reflection backend, also set `threadCheckerClassName` in `parameters.properties` (step 2e) and make sure the checker class/module is on the runtime classpath in that mode too — otherwise thread checks silently disappear from reflection builds, with no compile error. Register the checker-module dependency **unconditionally**: build plugins often early-return before the annotation-processing block when reflection mode is on, and a dependency added inside that block is silently dropped from reflection builds.

If the old asserter behavior differed per build variant, place the checker class (or the option) in the matching variant/source-set configuration; if the build system can't scope it, a single checker that decides at runtime is acceptable. Then delete all `setThreadAsserter` calls — including entries that reference the old thread-asserter machinery *by name* in baseline profiles, keep rules, and similar dumps; search by the `ThreadAsserter` **type** (entries look like `Lcom/yandex/yatagan/ThreadAsserter;` or `...internal/ThreadAssertions;->setAsserter(...)`, and the internal setter no longer exists in 2.0), or the final re-scan will not reach zero. Also remove the `yatagan.experimental.omitThreadChecks` option — omitting `threadCheckerClassName` now means "no checks".

### 2e. Reflection backend: programmatic setup → `parameters.properties`

`setupReflectionBackend()`, `resetReflectionBackend()`, and the `Initializer` fluent API are removed. (When searching, don't grep bare `Initializer` — it drowns in Android/app initializers; use the `setupReflectionBackend` call or `yatagan.*Initializer` imports as anchors.) The reflection backend is auto-discovered when `api-dynamic` is on the runtime classpath and configured via a classpath resource:

```properties
# src/<sourceSet>/resources/META-INF/com.yandex.yatagan.reflection/parameters.properties
validationDelegateClass=com.example.MyValidationDelegate
maxIssueEncounterPaths=3
enableStrictMode=true
```

All keys optional; unknown keys are an error. Full set: `validationDelegateClass`, `maxIssueEncounterPaths` (int), `enableStrictMode` (bool), `usePlainOutput` (bool), `enableDaggerCompatibility` (bool), `threadCheckerClassName`. Mapping from the old fluent calls: `.validation(...)` → `validationDelegateClass`, `.maxIssueEncounterPaths(...)` → same-named key, `.strictMode(...)` → `enableStrictMode`. Removed without replacement — delete calls: `resetReflectionBackend()` (per-classloader soft cache now), `useCompiledImplementationIfAvailable(...)` (generated impl always preferred), `reportDuplicateAliasesAsErrors(...)` (always an error now), `allConditionsLazy(...)`. Exception: **`logger(...)` is moved, not deleted** — carry its body into the new `val logger` property on your `DynamicValidationDelegate` (step 2f) before removing the call.

After deleting project-side wrappers, also purge baseline-profile/keep-rule entries naming the deleted *project* symbols (e.g. `...ValidationKt;->setupValidation(...)`) — same cleanup 2d prescribes for Yatagan's own removed API.

Per-build-type setup code is replaced by placing the properties file in the desired source set (e.g. `debug`). Two traps:

- **Wrapper chains.** The removed calls often sit inside a project-level helper (`setupValidation(...)` etc.) that has call sites in other files and may have a no-op twin in a stub source set, each with its own build registration. Delete the whole wrapper chain — including the now-dead stub source set's build registration and, if the environment allows, the stub directory itself (if file deletion is blocked, empty the file and flag the directory for manual removal).
- **Manually registered source sets.** If the project registers reflection-related source dirs by hand (custom source sets, property-gated variants) instead of standard AGP source sets, the properties file's `resources` dir must be registered the same way — a misplaced properties file fails **silently**. Because the failure is silent, verify positively: run the module's process-resources task (or build the artifact) and confirm `META-INF/com.yandex.yatagan.reflection/parameters.properties` appears in the packaged output.

### 2f. `DynamicValidationDelegate` interface changes

Implementations of `com.yandex.yatagan.rt.support.DynamicValidationDelegate` must:
- add the new `val logger: Logger?` property (replaces `Initializer.logger(...)`);
- change `ReportingDelegate.reportError/reportWarning` parameters from `RichString` to `String`;
- have a **public no-arg constructor** to be usable via `validationDelegateClass`. If the existing delegate takes constructor parameters, the usual fix is to make the delegate itself no-arg and resolve the parameter lazily from a **pre-existing** static holder (Android `Application` singleton, Chromium `ContextUtils`, an app-graph accessor) — that's fine; adding a *new* mutable global is not. If no such holder exists, note it and leave the delegate unregistered.

### 2g. SPI / validation plugins

The SPI modules (`lang-api`, `core-model-api`, `core-graph-api`, `validation-api`, `validation-spi`) are source- and binary-incompatible. The skill cannot enumerate every change — **inspecting the published 2.0 artifacts is expected here** whenever a symbol fails to resolve. Prefer the `-sources.jar` published next to each binary jar (full KDoc and exact member lists); fall back to `javap -classpath <jar>` if sources aren't published.

**Headline change — visitor adapters are gone.** The `*VisitorAdapter` base classes (`BindingVisitorAdapter`, `AnnotationValueVisitorAdapter`, …) are removed. Visitors are now plain interfaces with default methods plus one abstract fallback — and the fallback's name/signature differs per interface:

| 1.x | 2.0 |
|---|---|
| `BindingVisitorAdapter<R>()` + `visitDefault()` | implement `Binding.Visitor<R>` + `visitOther(binding: Binding)` |
| `HasNodeModel.Visitor<R>` + `visitDefault()` | same interface, fallback is `visitOther()` — **no parameter** |
| `AnnotationValueVisitorAdapter<R>()` + `visitDefault()` | implement `Annotation.Value.Visitor<R>`; fallback keeps the name but gains a parameter: `visitDefault(value: Any?)` |
| `Callable.Visitor<T>` (any subset) | must also implement `visitOther(callable: Callable)` |
| `ModuleHostedBindingModel.Visitor<R>` (any subset) | must also implement `visitOther(model: ModuleHostedBindingModel)` |

Renames and semantic changes:

| 1.x | 2.0 |
|---|---|
| `ComponentModel.modules: Set<ModuleModel>` (transitive) | `modules: List<ModuleModel>` (direct only); transitive set is `allModules` |
| `SubComponentBinding` | `SubComponentFactoryBinding` |
| `Binding.Visitor.visitSubComponent` | `visitSubComponentFactory` |
| `ObjectCache`-based lang machinery | `LexicalScope` concept; `Extensible` reworked |

**Unchanged — verify before "fixing":** `BindingGraph.modules` (the `modules`/`allModules` change is on `ComponentModel` only — don't blindly apply it here and silently change semantics), `ConditionScope` (already an interface with `isTautology()`/`isContradiction()`/`implies()` and nested `Never`/`Always` in 1.6.2 — no migration required; optional sanctioned cleanup: replace identity comparisons like `conditionScope == ConditionScope.Never` with `isContradiction()`, which also covers expression scopes that evaluate to a contradiction), `BaseBinding.target`, `NodeModel.multiBoundListNodes()`, `AnnotationDeclaration.attributes`, the `HasNodeModel.accept` extension. Parts of the API are now `@Internal`/`@Incubating` and may emit "incubating" warnings — that's expected, not an error.

### 2h. Generated class names: `Yatagan$` → `Yatagan…`

| Component | 1.x impl | 2.0 impl |
|---|---|---|
| `MyComponent` (top-level) | `Yatagan$MyComponent` | `YataganMyComponent` |
| `Outer.Inner` (nested) | `Yatagan$Outer$Inner` | `YataganOuter_Inner` |

Search with the escaped pattern `Yatagan\$` across ProGuard/R8 rules, `Class.forName` lookups, baseline profiles, serialized class names, and build tooling that constructs these names; update to the new scheme. The `$`-handling rule differs by context — don't mix them up:

- **Dumps of generated classes** (baseline profiles, stack traces, serialized names): only the component's own name changes; the generated implementation's *internal* nested classes keep their `$` separators (`YataganMyComponent$SomeImpl$CachingProviderImpl`), so in a deeply nested entry replace **only the first `$`**. Verify against an actually-generated class after a build. Also: **only rewrite components declared in the project being migrated** — profiles record classes from prebuilt third-party dependencies still generating 1.x names; leave those entries alone.
- **Tooling constructing an implementation name from a source-level component**: here *all* of the component's own nesting separators change — `Outer$Inner` → `YataganOuter_Inner`. Apply the replacement to the **bare simple name only**, then prepend: `"$packageName.Yatagan" + simpleName.replace('$', '_')` — replacing on the fully-qualified string would mangle other `$`s.

The third-party rule applies to **build tooling** too, with a twist: a bytecode transform or class-lookup that runs over the whole runtime classpath will encounter *both* schemes at once — 2.0 names for this project's components, legacy `Yatagan$` names for components inside prebuilt 1.x libraries. Such tooling must accept both: try the 2.0 name first, fall back to the legacy `Yatagan$` name. Rewriting it to the new scheme only produces failures like `Yatagan generated implementation (...) is not found` for prebuilt components — and if the tooling is behind a default-off flag, the default build won't catch it.

Code that only uses `Yatagan.builder()`/`autoBuilder()`/`create()` needs nothing — the loader falls back to legacy names, and bundled consumer rules in `api-public` keep both patterns.

### 2i. Artifacts

Background that makes this step make sense: in 1.x, `api-public` deliberately did **not** contain the `Yatagan` entry-point class (that lived in `api-compiled`/`api-dynamic`); in 2.0 the entry point moved into `api-public`, and `api-compiled:2.0.0` is published as a thin pom-only alias re-exporting it. Hence:

- `api-common` is **removed** — replace the dependency with `api-public`.
- `api-compiled` is a deprecated alias — replace with `com.yandex.yatagan:api-public`. If the project already declares `api-public`, collapse the two declarations and update their call sites rather than leaving two names for the same coordinate.
- **Prebuilt dependencies keep pulling the old coordinate.** Removing your own `api-compiled` declaration is not enough: any dependency built against Yatagan 1.x still requests `api-compiled` transitively, and since `api-compiled` and `api-public` are different coordinates carrying the same classes, the build fails with `Duplicate class com.yandex.yatagan.Yatagan found in modules api-compiled-... and api-public-...`. Add (or keep and widen) a dependency substitution mapping `com.yandex.yatagan:api-compiled` → `api-public`, until every prebuilt is rebuilt against 2.0. If the project carries an existing substitution annotated with a TODO like "remove after upgrading to Yatagan 2.0" — that TODO is wrong: the substitution must survive (and usually widen to all modes) as long as any prebuilt still requests `api-compiled`.

### 2j. Processor options cleanup

Remove `yatagan.experimental.omitThreadChecks` and `yatagan.experimental.reportDuplicateAliasesAsErrors` (duplicate-alias check is now always an error). All other options (`yatagan.enableStrictMode`, `yatagan.usePlainOutput`, `yatagan.maxIssueEncounterPaths`, `yatagan.experimental.allConditionsLazy`, `yatagan.experimental.omitProvisionNullChecks`, `yatagan.experimental.maxSlotsPerSwitch`) are unchanged.

## Unchanged — do NOT touch

`Yatagan.builder()`/`autoBuilder()`/`create()` call sites, `@Component`, `@Module`, `@Binds`, `@BindsInstance`, `@IntoList`/`@IntoSet`/`@IntoMap`/`@Multibinds`, `@AssistedInject`/`@AssistedFactory`, `Lazy<T>`, `Optional<T>`, `Provider<T>`, scopes, `@Conditional` and its container `@Conditionals` on classes, and the `-opt-in` compiler markers `com.yandex.yatagan.ConditionsApi`/`VariantApi` (both still exist in 2.0).

## Step 3 — Build, fix, verify

Run the repo's build check. Triage errors:

- `Missing binding for ...` + "seems to be a child component" → a missed 2c site.
- "Child components can't have a factory methods declared…" → 2c: builder XOR parent factory method.
- Conflicting/duplicate bindings errors that were warnings in 1.x → real graph issue, now always an error; fix the duplicate binding.
- Unresolved `Conditional` inside `@Provides(...)` → missed 2a form (check named/array/multi-line forms).
- Unresolved `setupReflectionBackend`/`Initializer` → missed 2e call.
- `Duplicate class com.yandex.yatagan.Yatagan found in modules api-compiled-... and api-public-...` → 2i: a prebuilt dependency still pulls `api-compiled`; add the dependency substitution.
- `Yatagan generated implementation (...) is not found` for a class from a prebuilt library → 2h: build tooling must fall back to legacy `Yatagan$` names.
- Transient daemon/toolchain crash → environmental; retry once.

**Verification coverage.** A repo's default build check may be compile-only and may skip flag-gated code paths. Before declaring done, list which migrated areas the check actually exercised. Anything Yatagan-related behind a build flag or an alternative source set — reflection-mode source sets, R8/ProGuard processing, bytecode transforms that construct generated class names — must be built separately with the flag/variant enabled, or explicitly reported as unverified. Two non-failures to not chase: stale `Yatagan$...` generated sources under *other variants'* output dirs are leftovers from the pre-migration build (only the variants your check builds get regenerated); and if `<module>/build` doesn't exist, the repo may relocate build dirs — ask Gradle (`./gradlew -q :<module>:properties | grep buildDir`) before concluding output is missing.

Done when: build is green, zero Yatagan deprecation warnings remain (pre-existing non-Yatagan warnings are out of scope), every Step 0 inventory item is either migrated or confirmed absent by a zero-hit re-scan, and every migrated area is either exercised by a build or explicitly reported as unverified.
