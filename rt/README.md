# Reflection in Yatagan

## Performance

As of performance, reflection currently uses global object cache and eagerly traverses DI graph hierarchies,
as codegen does, because the core code is fully shared.
For large DI graph hierarchies it may take from hundreds of milliseconds to a couple of seconds to parse all necessary
constructs and build a graph, so startup times may be penalized,
depending on the platform, device and graph size and contents.
However, practice shows, that startup time losses are insignificant compared to build speed gains,
so reflection mode is well suited for debug builds, which are often less performant themselves anyway.

Global cache usage may increase app's memory consumption, though no measurements are done in the matter.

## Reflection specific API

An entry-point for reflection, `com.yandex.yatagan.Yatagan` object, 
contains additional methods to tweak Yatagan's behavior, that are normally available as annotation processor
options in compile time. 

To use those, call `Yatagan.setupReflectionBackend()`, perform necessary setup with a chained calls 
and ultimately finish with `apply()` call to apply changes.

Technically, reflection session along with global object cache can be reset and cleaned up 
via `Yatagan.resetReflectionBackend()` call, though it's often best to not use this at all -
check the [correspondent kdocs][Y] on the matter.

As this reflection-specific api is only present in `api-dynamic` delegate, one must extract the code
that utilizes it into a specific source set directory, that is only used, when the reflection backend is being used.
Something along these lines may do the trick:
```kotlin
// build.gradle.kts
kotlin.sourceSets.main {
    if (useReflection) {  // e.g. for debug builds
        // The actual implementation that utilizes `setupReflectionBackend()` API
        kotlin.srcDir(file("src/yatagan-reflection"))
    } else {
        // The stub implementation that is no-op
        kotlin.srcDir(file("src/yatagan-no-reflection"))
    }
}
```
And then call the code early in your application initialization routine.
This way, it will perform necessary initialization when the app is assembled with reflection and do nothing otherwise.

### Validation

Reflection, by default has _no conventional validation enabled_ - this is done for performance’s sake.
If graph contains an error, then the **behavior is, technically, undefined**.
In practice, if the error is, in fact, critical, the more or less informative exception will be thrown. 
However, in some cases, the graph will proceed to function, maybe incorrectly.

There's an option to enable **full validation for reflection-backed graphs**:
```kotlin
Yatagan.setupReflectionBackend()
    .validation(MyValidationDelegateImpl()/* your validation delegate implementation */)
    // optionally other setup
    .apply()
```

## Qualifier, Scope, etc. retention

If, say, a scope or qualifier annotation has non-runtime retention, then it might work correctly
for code generation backends, and fail to work for reflection backend, silently introducing inconsistencies.
Thus, code generation backends will report non-runtime retention for the sake of compatibility with
reflection.

[Y]: ../api/dynamic/src/main/kotlin/Yatagan.kt