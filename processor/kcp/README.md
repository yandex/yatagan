# Kotlin compiler plugin backend proof of concept

This module is the proof of concept described in `KCP_BACKEND_RESEARCH.md`. It is pinned to Kotlin 2.4.10 and
uses K2 IR compiler APIs directly. Compiler-version compatibility and Dagger compatibility are outside this
proof of concept.

## What is implemented

The compiler plugin adapts Kotlin IR through `:lang:kcp`, then uses Yatagan's shared `ComponentModel`,
`BindingGraph`, and validation pipeline. Processor options and decorated validation diagnostics therefore follow
the existing backends. Java sources are skipped by the KCP compile-test driver; the current target is Kotlin
declarations.

The existing `LangTestDriver` conformance harness still covers JAP, KSP, and RT only because its Room compiler
host embeds Kotlin 2.3. KCP is covered instead by compiler-driven `:lang:kcp` integration tests running on the
pinned compiler, plus every Kotlin case in the shared `:testing:tests` corpus.

Native IR generation is deliberately smaller than validation. Its working subset is:

- root interface components created with `Yatagan.create()`;
- direct, unscoped constructor injection, including transitive constructor dependencies;
- direct, unscoped Kotlin `@Provides` functions on object modules;
- loader-compatible implementation names for top-level and nested components.

The emitter rejects a graph before changing IR when it contains unsupported semantics. The unsupported subset
currently includes explicit builders/factories, component dependencies, subcomponents, member injection,
scopes, framework wrappers such as `Provider` and `Lazy`, conditions, assisted injection, multibindings,
generic provision targets, and module instances. This is intentional PoC scope, not a silent fallback.

Set the plugin option below to run the shared model and validation without native generation:

```text
-P plugin:com.yandex.yatagan:yatagan.kcp.codegen=false
```

Validation-only applications must use `:api:dynamic` at runtime, because no loader implementation is emitted.
Native-generation applications use `:api:public` and do not need the dynamic backend.

## Gradle wiring in this repository

`processor:kcp` is not a self-contained shadow jar. The `kcpPlugin` configuration is the explicit compiler
plugin bundle: it resolves the processor plus its runtime dependencies, and every resolved jar is passed to K2
in one comma-separated `-Xplugin` argument. The standalone consumer in `:testing:kcp-consumer` uses this setup:

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

For validation-only use, replace `:api:public` with `:api:dynamic` and add the `yatagan.kcp.codegen=false`
option shown above to `freeCompilerArgs`.

## Try it

From the repository root:

```shell
./gradlew :testing:kcp-consumer:clean :testing:kcp-consumer:test
./gradlew :lang:kcp:check :codegen:ir:check :processor:kcp:test
./gradlew :testing:tests:test
```

The first command compiles and runs a normal Gradle consumer through native code generation. The processor and
IR integration tests invoke the pinned Kotlin 2.4.10 compiler. The shared test corpus also runs KCP in an
isolated Kotlin 2.4.10 process because Room compile-testing embeds a different compiler version.
