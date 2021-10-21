pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

include("api")
include("ksp-driver")
include("core")
include("runtime")
include("generator")
include("generator:poetry")
include("testing")
include("kapt")
