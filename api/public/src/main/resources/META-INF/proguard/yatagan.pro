# Keep generated components, as they are accessed with a reflection call.
-keep class **.Yatagan* {
    # Keep creating methods - called via reflection.
    public * autoBuilder();
    public * builder();
}

# Keep component interfaces, as their names must be be preserved to find correspondent implementation.
-keep @com.yandex.yatagan.Component interface *

# Keep component builder interfaces, to allow finding their component interfaces.
-keep @com.yandex.yatagan.Component$Builder interface *

# Remove null-checks in release.
-assumenosideeffects class com.yandex.yatagan.internal.Checks {
    public static void assertNotNull(...);
}