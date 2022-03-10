# Keep generated components, as they are accessed with a reflection call.
-keep class **.Dagger$* {
    # Keep creating methods - called via reflection.
    public * create();
    public * builder();
}

# Keep component interfaces, as their names must be be preserved to find correspondent implementation.
-keep @com.yandex.daggerlite.Component interface *

# Keep component builder interfaces, to allow finding their component interfaces.
-keep @com.yandex.daggerlite.Component$Builder interface *

# Remove null-checks in release.
-assumenosideeffects class com.yandex.daggerlite.internal.Checks {
    public static void assertNotNull(...);
}