# Reflection in Yatagan

## Performance

As of performance, reflection eagerly traverses DI graph hierarchies,
as codegen does, because the core code is fully shared.
For large DI graph hierarchies it may take from hundreds of milliseconds to a couple of seconds to parse all necessary
constructs and build a graph, so startup times may be penalized,
depending on the platform, device and graph size and contents.
However, practice shows, that startup time losses are insignificant compared to build speed gains,
so reflection mode is well suited for debug builds, which are often less performant themselves anyway.

Reflection data is cached per-class-loader, and the cache entries are softly referenced,
so they can be reclaimed under memory pressure. There is no manual cache reset API.

## Reflection specific API

The reflection backend is discovered automatically by the common `com.yandex.yatagan.Yatagan` entry-point
when the `api-dynamic` artifact is present on the runtime classpath.
A generated implementation, if present, is still preferred.

Options, that are normally available as annotation processor options in compile time,
are configured for the reflection backend via a classpath resource:

```
META-INF/com.yandex.yatagan.reflection/parameters.properties
```

Place the file into the resources of the desired source set (e.g. `src/debug/resources` on Android
to only configure the debug build). Supported keys (all optional; unknown keys are an error):

| Property                 | Type                                                                       |
|--------------------------|----------------------------------------------------------------------------|
| `validationDelegateClass` | FQN of a [DynamicValidationDelegate][DVD] impl with a public no-arg constructor |
| `maxIssueEncounterPaths` | int                                                                        |
| `enableStrictMode`       | boolean                                                                    |
| `usePlainOutput`         | boolean                                                                    |
| `enableDaggerCompatibility` | boolean                                                                 |
| `threadCheckerClassName` | FQN of a thread checker class                                              |

The parameters are loaded asynchronously on a background daemon thread,
so backend initialization doesn't block on classpath I/O.

### Validation

Reflection, by default has _no conventional validation enabled_ - this is done for performance’s sake.
If graph contains an error, then the **behavior is, technically, undefined**.
In practice, if the error is, in fact, critical, the more or less informative exception will be thrown. 
However, in some cases, the graph will proceed to function, maybe incorrectly.

There's an option to enable **full validation for reflection-backed graphs** - specify your
[DynamicValidationDelegate][DVD] implementation in `parameters.properties`:

```properties
validationDelegateClass=com.example.MyValidationDelegateImpl
```

## Qualifier, Scope, etc. retention

If, say, a scope or qualifier annotation has non-runtime retention, then it might work correctly
for code generation backends, and fail to work for reflection backend, silently introducing inconsistencies.
Thus, code generation backends will report non-runtime retention for the sake of compatibility with
reflection.

[DVD]: support/src/main/kotlin/com/yandex/yatagan/rt/support/DynamicValidationDelegate.kt
