pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "dagger-lite"

include("api")

include("base")

include("validation")
include("validation-impl")

include("lang")

include("lang-ksp")
project(":lang-ksp").projectDir = file("lang/ksp")

include("lang-jap")
project(":lang-jap").projectDir = file("lang/jap")

include("core")
include("core-impl")

include("graph")
include("graph-impl")

include("processor")

include("processor-jap")
project(":processor-jap").projectDir = file("processor/jap")

include("processor-ksp")
project(":processor-ksp").projectDir = file("processor/ksp")

include("generator")
include("generator-poetry")
include("generator-lang")

include("testing")

include("spi")

project(":validation-impl").projectDir = file("validation/impl")

project(":core-impl").projectDir = file("core/impl")

project(":graph").projectDir = file("graph")
project(":graph-impl").projectDir = file("graph/impl")

project(":generator-poetry").projectDir = file("generator/poetry")
project(":generator-lang").projectDir = file("generator/lang")