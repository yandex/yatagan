# Reflection in Yatagan

## Performance

As of performance, reflection caches parsed model state and eagerly traverses DI graph hierarchies,
as codegen does, because the core code is fully shared.
For large DI graph hierarchies it may take from hundreds of milliseconds to a couple of seconds to parse all necessary
constructs and build a graph, so startup times may be penalized,
depending on the platform, device and graph size and contents.
However, practice shows, that startup time losses are insignificant compared to build speed gains,
so reflection mode is well suited for debug builds, which are often less performant themselves anyway.

Cached model state may increase app's memory consumption, though no measurements are done in the matter.

## Reflection configuration

Reflection uses the same `com.yandex.yatagan.Yatagan` entry point as generated implementations.
Add `com.yandex.yatagan:api-dynamic` to the runtime classpath and keep using
`Yatagan.create()`, `Yatagan.builder()` or `Yatagan.autoBuilder()`.

The entry point first tries to load a generated implementation. If none is found and `api-dynamic` is present,
it falls back to reflection.

Reflection-specific options are read from an optional classpath resource:

```text
META-INF/com.yandex.yatagan.reflection/parameters.properties
```

For example, in a Gradle/JVM project this can be placed at
`src/main/resources/META-INF/com.yandex.yatagan.reflection/parameters.properties`.

Supported properties:

```properties
validationDelegateClass=com.example.MyDynamicValidationDelegate
maxIssueEncounterPaths=5
enableStrictMode=true
usePlainOutput=false
enableDaggerCompatibility=false
threadCheckerClassName=com.example.MyThreadAssertions
```

All properties are optional. Unknown properties are reported as errors. `validationDelegateClass` must name a class
that implements [DynamicValidationDelegate][DVD] and has a no-arg constructor.

### Validation

Reflection validates graphs by default with [SimpleDynamicValidationDelegate][SDVD].
By default, validation runs synchronously, reports through the console logger, and throws if errors are found.

To customize validation behavior, provide `validationDelegateClass` in `parameters.properties`.
Custom delegates can use [AsyncDynamicValidationDelegate][ADVD] if validation should be scheduled asynchronously.

## Qualifier, Scope, etc. retention

If, say, a scope or qualifier annotation has non-runtime retention, then it might work correctly
for code generation backends, and fail to work for reflection backend, silently introducing inconsistencies.
Thus, code generation backends will report non-runtime retention for the sake of compatibility with
reflection.

[DVD]: ../rt/support/src/main/kotlin/com/yandex/yatagan/rt/support/DynamicValidationDelegate.kt
[SDVD]: ../rt/support/src/main/kotlin/com/yandex/yatagan/rt/support/SimpleDynamicValidationDelegate.kt
[ADVD]: ../rt/support/src/main/kotlin/com/yandex/yatagan/rt/support/AsyncDynamicValidationDelegate.kt
