pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

include("api")
include("ksp-driver")
include("ksp-driver:runtime-internals")
include("core")
include("runtime")
include("generator")
include("generator:poetry")
