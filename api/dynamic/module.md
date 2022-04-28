# Module api-dynamic

An API dependency for clients, that wish to use reflection for parsing and serving DL components at runtime
(RT backend).

Specializes base [:api] dependency.
Provides an entry-point implementation [Dagger][com.yandex.daggerlite.Dagger], that uses the same logic, as AP backends,
at runtime to process annotations. The components and other required interfaces are implemented using
`java.lang.reflect.Proxy` mechanism.

## Validation

Currently, no full validation is performed when using RT backend, as it's very expensive in runtime.

Strictly speaking, RT only works with well-formed code, if the code contains errors, behavior is _undefined_.
In practice some non-breaking errors may be fully ignored; for other kinds of errors, like missing bindings,
a readable crash will happen.

There are plans to introduce full validation in some way. See the [issue](https://st.yandex-team.ru/DAGGERLITE-32).

## Minifier support (Proguard/R8)

Currently, there's no support for code shrinkers/minifiers for RT,
as it's not trivial to write a correct config for such tools for RT.

There are plans to support minifying, yet they have low priority, as RT is primarily designed to speed up debug builds.
See the [issue](https://st.yandex-team.ru/DAGGERLITE-33).

## Android-specific notes

Reflection backend requires **Android API Level 24+**.
See the corresponding [issue](https://st.yandex-team.ru/DAGGERLITE-25).