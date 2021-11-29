pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "dagger-lite"

include("api")

include("base")

include("lang")

include("core")
include("core-impl")

include("graph")
include("graph-impl")

include("process")

include("ksp-driver")
include("ksp-driver-lang")

include("jap-driver")
include("jap-driver-lang")

include("runtime")
include("runtime-lang")

include("generator")
include("generator-poetry")
include("generator-lang")

include("testing")

project(":lang").projectDir = file("lang")

project(":core-impl").projectDir = file("core/impl")

project(":graph").projectDir = file("graph")
project(":graph-impl").projectDir = file("graph/impl")

project(":ksp-driver-lang").projectDir = file("ksp-driver/lang")
project(":jap-driver-lang").projectDir = file("jap-driver/lang")
project(":runtime-lang").projectDir = file("runtime/lang")

project(":generator-poetry").projectDir = file("generator/poetry")
project(":generator-lang").projectDir = file("generator/lang")