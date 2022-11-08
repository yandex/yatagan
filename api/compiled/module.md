# Module api-compiled

A lightweight API dependency for clients, that wish to utilize code generation for Yatagan components.

 Specializes base [:api] dependency.  
 Provides an entry-point implementation [Yatagan][com.yandex.yatagan.Yatagan], that simply loads generated classes.

For the entry-point to work, Yatagan component implementations should be generated using one of the AP backends.
 Currently, Yatagan supports these:
  - KAPT via [:processor-jap]
  - KSP via [:processor-ksp] (experimental)