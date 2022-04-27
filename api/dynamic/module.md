# Module api-dynamic

An API dependency for clients, that wish to use reflection for parsing and serving DL components at runtime 
 (RT backend).

 Specializes base [:api] dependency.
 Provides an entry-point implementation [Dagger][com.yandex.daggerlite.Dagger], that uses the same logic, as AP backends, 
 at runtime to process annotations. The components and other required interfaces are implemented using 
 `java.lang.reflect.Proxy` mechanism.

## Android-specific notes

Reflection backend requires **Android API Level 24+**.
 See the corresponding [issue](https://st.yandex-team.ru/DAGGERLITE-25).