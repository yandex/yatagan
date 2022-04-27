# Module api-compiled

A lightweight API dependency for clients, that wish to utilize code generation for DL components.

 Specializes base [:api] dependency.  
 Provides an entry-point implementation [Dagger][com.yandex.daggerlite.Dagger], that simply loads generated classes.

For the entry-point to work, dagger-lite component implementations should be generated using one of the AP backends.
 Currently, DL supports these:
  - KAPT via [:processor-jap]
  - KSP via [:processor-ksp] (experimental)