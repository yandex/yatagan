# Validation plugins for Yatagan

Yatagan provides an SPI for implementing and providing custom plugins, that can inspect Yatagan graphs.
The interface, that needs to be implemented is [ValidationPluginProvider][VPP].
To make Yatagan see your plugin(s), you must provide a necessary SPI locator resource file with the resource path
`META-INF/services/com.yandex.daggerlite.spi.ValidationPluginProvider` with fully qualified provider class name(s):
```
com.example.MyClassImplementingValidationPluginProvider
com.example.AnotherOneIfNeeded
```

Note, that code generation from such a plugin is not supported, 
as the plugin may be actually used by reflection backend.
Current implementation is only fitting for implementing custom DI inspections.

## Usage

Once written, a plugin can be used as follows:
```kotlin
// build.gradle.kts
dependencies {
    // ..
    // use kapt or ksp configuration - the same, as you use to apply processor.
    kapt(project(":my-yatagan-plugins"))
}
```
And your plugins will be loaded and used by the framework.

### Using plugins with reflection backend

Plugins can be used with reflection backend as well.
In order to do so, one must provide [DynamicValidationDelegate][DVD] implementation that returns `true` from its 
`usePlugins` property. 

This opt-in is required, as locating and loading plugins may bring additional performance penalties, 
though they can be minimized if [asynchronous validation delegate implementation][ADVD] is used.

Then the plugins dependency must be present in application's runtime classpath, like this:
```kotlin
dependencies {
    // ..
    runtimeOnly(project(":my-yatagan-plugins"))
}
```

Read on how to specify a validation delegate [here][RT].

[VPP]: src/main/kotlin/ValidationPluginProvider.kt
[DVD]: ../../rt/support/src/main/kotlin/DynamicValidationDelegate.kt
[ADVD]: ../../rt/support/src/main/kotlin/AsyncDynamicValidationDelegate.kt
[RT]: ../../rt/README.md#validation