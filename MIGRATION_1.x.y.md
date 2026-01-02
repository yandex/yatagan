# Migration from 1.x.y to 2.0

This guide covers migrating Yatagan projects from any 1.x.y release to Yatagan 2.0.

## Table of Contents

1. [New Features](#new-features)
2. [Breaking Changes](#breaking-changes)
3. [Deprecated APIs](#deprecated-apis)
4. [Processor Options](#processor-options)

---

## New Features

### 1. Dagger Compatibility Mode

An experimental mode that allows Yatagan to recognize Dagger annotations, enabling gradual migration.

**Enable via processor option:**
```kotlin
ksp {
    arg("yatagan.experimental.enableDaggerCompatibility", "true")
}
```

**Features:**
- Recognizes Dagger annotations (`@dagger.Component`, `@dagger.Module`, etc.)
- Compiled backends generate `Dagger<ComponentName>` bridge classes alongside Yatagan implementations
- Yatagan and Dagger annotations can coexist; Yatagan takes priority when both present
- Reflection backend also supports Dagger-compatible graphs via `enableDaggerCompatibility=true`, but uses the usual `Yatagan.create()` / `Yatagan.builder()` entry points

---

### 2. Unified Entry Point

The `Yatagan` object in `api:public` now automatically handles both compiled and reflection backends:

```kotlin
// Works with both backends
val component = Yatagan.create(MyComponent::class.java)
val builder = Yatagan.builder(MyComponent.Builder::class.java)
```

The loader tries compiled implementation first, then falls back to reflection if available.

---

## Breaking Changes

### 1. `@Provides` Conditionals Removed

The `conditionals` parameter has been removed from `@Provides`. Use `@Conditional` as a separate annotation.

**Before (1.x.y):**
```kotlin
@Module
object MyModule {
    @Provides(Conditional(FeatureA::class))
    fun provideFoo(): Foo = FooImpl()
}
```

**After (2.0):**
```kotlin
@Module
object MyModule {
    @Provides
    @Conditional(FeatureA::class)
    fun provideFoo(): Foo = FooImpl()
}
```

---

### 2. Generated Component Class Names

Generated implementation class names have changed to be more user-friendly, so that they can be used directly.

**Before (1.x.y):**
```
Yatagan$MyComponent          // Top-level component
Yatagan$Outer$Inner          // Nested component
```

**After (2.0):**
```
YataganMyComponent           // Top-level component (no separator)
YataganOuter_Inner           // Nested component (underscore instead of $)
```

**Impact:**
- If you use `Yatagan.create()` or `Yatagan.builder()` APIs, no changes needed
- If you reference generated class names directly (reflection, ProGuard rules), update them
- The runtime loader tries the new name first, then falls back to legacy names for compatibility

---

### 3. Subcomponent Access Uses Factory Methods

Direct binding/injection of subcomponent instances is no longer allowed. In addition, every abstract component method
returning a non-root component is now treated as a factory method, even when it has no parameters.

**Before (1.x.y):**
```kotlin
@Component(modules = [ParentModule::class])
interface ParentComponent {
    val child: ChildComponent               // Direct binding
    fun getChild(): ChildComponent          // Entry-point in 1.x.y
}

@Module(subcomponents = [ChildComponent::class])
interface ParentModule

@Component(isRoot = false)
interface ChildComponent
```

**After (2.0):**
```kotlin
@Component(modules = [ParentModule::class])
interface ParentComponent {
    fun createChild(): ChildComponent       // Factory method
}

@Module(subcomponents = [ChildComponent::class])
interface ParentModule

@Component(isRoot = false)
interface ChildComponent
```

**Impact:**
- Review any parameterless methods returning child components: in 2.0 they are factories, not entry-points
- If a child component declares an explicit `@Component.Builder`, expose or inject `ChildComponent.Builder` instead of `ChildComponent`

---

### 4. Reflection Backend Configuration

The programmatic reflection-specific API has been replaced with an optional properties file.

**Before (1.x.y):**
```kotlin
// In api:dynamic, Yatagan object had setup methods:
Yatagan.setupReflectionBackend()
    .validation(SimpleDynamicValidationDelegate())
    .maxIssueEncounterPaths(5)
    .strictMode(true)
    .apply()
```

**After (2.0):**

If you need to customize the reflection backend, create a properties file at
`src/main/resources/META-INF/com.yandex.yatagan.reflection/parameters.properties`:

```properties
validationDelegateClass=com.yandex.yatagan.rt.support.SimpleDynamicValidationDelegate
maxIssueEncounterPaths=5
enableStrictMode=true
usePlainOutput=false
enableDaggerCompatibility=false
threadCheckerClassName=com.example.MyThreadAssertions
```

The file is optional. Without it, reflection still works and now validates graphs by default using
`SimpleDynamicValidationDelegate`.

The old runtime methods `setupReflectionBackend()` and `resetReflectionBackend()` are gone.
`useCompiledImplementationIfAvailable()` is no longer configurable, because the unified `Yatagan` entry point always
tries compiled implementations first. `allConditionsLazy()` also no longer has a reflection-specific replacement.

The unified `Yatagan` entry point in `api:public` automatically detects and uses the reflection backend when:
1. The `api:dynamic` module is on the classpath
2. No compiled implementation is found

#### DynamicValidationDelegate Interface Changes

If you have a custom `DynamicValidationDelegate` implementation:

1. **Constructor requirement**: Custom delegates referenced in `parameters.properties` must have a **no-arg constructor** (instantiated via reflection)

2. **Logger property**: Now required to implement `logger` property (previously set via builder):
   ```kotlin
   override val logger: Logger? = ...
   ```

3. **Reporting methods signature change**:
   ```kotlin
   // Before (1.x.y)
   override fun reportError(message: RichString)
   override fun reportWarning(message: RichString)

   // After (2.0)
   override fun reportError(message: String)
   override fun reportWarning(message: String)
   ```

4. **Behavior change**: reflection no longer has a validation-less mode; duplicate alias conflicts are now always reported as errors

---

### 5. Thread Assertions API Redesign

The global `Yatagan.setThreadAsserter()` API has been removed. Thread assertions are now configured at compile-time via a processor option.

**Before (1.x.y):**
```kotlin
// Runtime configuration via global state
Yatagan.setThreadAsserter(MyThreadAsserter())
// or
ThreadAssertions.setAsserter(MyThreadAsserter())
```

**After (2.0):**
```kotlin
// 1. Create a class with a static method or Kotlin object:
object MyThreadAssertions {
    @JvmStatic
    fun assertThreadAccess() {
        // Your thread checking logic - throw if wrong thread
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Must be called on main thread"
        }
    }
}

// 2. Configure via processor option in build.gradle.kts:
ksp {
    arg("yatagan.threadCheckerClassName", "com.example.MyThreadAssertions")
}
// or for KAPT:
kapt {
    arguments {
        arg("yatagan.threadCheckerClassName", "com.example.MyThreadAssertions")
    }
}
```

**Important:** The class does NOT need to implement any interface. Requirements for the method:
- Must be named `assertThreadAccess`
- Must be either **static** or in a **Kotlin object**
- Must be **public** or **internal**
- Must have **no parameters**

For reflection backend, set the same class name in `parameters.properties` via `threadCheckerClassName=...`.

If no `yatagan.threadCheckerClassName` is specified, thread checking is disabled (equivalent to the old `yatagan.experimental.omitThreadChecks=true`).

---

## Deprecated APIs

These APIs still work in 2.0 but are deprecated and will be removed in future versions.

### Legacy Conditions API

| Annotation | Replacement | Notes |
|------------|-------------|-------|
| `@Condition` | `@ConditionExpression` | Use expression syntax |
| `@AnyCondition` | `@ConditionExpression` with `\|` | OR operator |
| `@AllConditions` | `@ConditionExpression` with `&` | AND operator |
| `@AnyConditions` | `@ConditionExpression` | Combined expressions |

**Before (legacy):**
```kotlin
@Condition(Flags::class, "INSTANCE.isFeatureEnabled")
annotation class FeatureA

@AnyCondition(
    Condition(Flags::class, "INSTANCE.isDebug"),
    Condition(Flags::class, "INSTANCE.isTesting"),
)
annotation class DebugOrTest
```

**After (recommended):**
```kotlin
@ConditionExpression("INSTANCE.isFeatureEnabled", Flags::class)
annotation class FeatureA

@ConditionExpression("INSTANCE.isDebug | INSTANCE.isTesting", Flags::class)
annotation class DebugOrTest
```

### Module Structure

#### `api:compiled` is deprecated

The `api:compiled` module is now an empty wrapper that re-exports `api:public`. It will be removed in a future release.

```kotlin
// Before
implementation("com.yandex.yatagan:api-compiled:$version")

// After
implementation("com.yandex.yatagan:api-public:$version")
```

#### `api:common` is removed

The internal `api:common` module has been removed. If you depended on it (not recommended), switch to `api:public`.

#### Validation/plugin API packages

If you implement validation plugins or other integrations against Yatagan's model/graph APIs, note that helper types
such as `Extensible`, `WithChildren`, and `WithParents` moved from `com.yandex.yatagan.core.graph` to
`com.yandex.yatagan.base.api`.

---

## Processor Options

### Removed Options

| Option | Reason |
|--------|--------|
| `yatagan.experimental.reportDuplicateAliasesAsErrors` | Now always enabled |
| `yatagan.experimental.omitThreadChecks` | Use `yatagan.threadCheckerClassName` (don't set = no checks) |

### New Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `yatagan.threadCheckerClassName` | String | null | Fully qualified class name with `assertThreadAccess()` method |
| `yatagan.experimental.enableDaggerCompatibility` | Boolean | false | Enable Dagger compatibility mode |
