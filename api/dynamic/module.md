# Module api-dynamic

An API dependency for clients, that wish to use reflection for parsing and serving Yatagan components at runtime
(RT backend).

Specializes base [:api] dependency.
Provides an entry-point implementation [Yatagan][com.yandex.yatagan.Yatagan], that uses the same logic, as AP backends,
at runtime to process annotations. The components and other required interfaces are implemented using
`java.lang.reflect.Proxy` mechanism.

## Optimized variant

One can depend on pre-optimized variant of this artifact: `com.yandex.yatagan:api-dynamic-optimized:<version>`.
It may provide improved performance in comparison with the default variant.

## Validation

No full validation is performed when using RT backend _by default_.
Although there's an API for opting in the full validation with SPI plugins support.
This API is designed in a way that allows the "good path" (when there are no errors in a graph) to perform 
un-penalized by validation.
Yet if the graph is invalid, all errors will be reported.
To make use of this, a [DynamicValidationDelegate][com.yandex.yatagan.DynamicValidationDelegate] implementation
may be supplied via [setDynamicValidationDelegate][com.yandex.yatagan.Yatagan.setDynamicValidationDelegate].

## Memory consumption

Every Yatagan backend maintains a global static cache of all the required framework models, 
that resemble a built Yatagan graph.
Though such an approach is fine when using it in compile-time, an actual application 
should be aware of **potential memory consumption increase**, when using reflection backend.
Strict measurements were not done on the subject though.

The other thing worth noting is that the reflection backend **has no means to clear the cache**, as it doesn't know
when the user is done using the graphs. The upside of this is very fast "warm-startup" for Android apps, 
though there's a potential for improvements here still.

## Minifier support (Proguard/R8)

Currently, there's no support for code shrinkers/minifiers for RT,
as it's not trivial to write a correct config for such tools for RT.

There are plans to support minifying, yet they have low priority, as RT is primarily designed to speed up debug builds.
See the [issue](https://st.yandex-team.ru/DAGGERLITE-33).

## Android-specific notes

Reflection backend requires **Android API Level 24+**.
See the corresponding [issue](https://st.yandex-team.ru/DAGGERLITE-25).