plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

rootProject.name = "yatagan"

include(":base:api")
include(":base:impl")

include(":api:public")
include(":api:compiled")  // DEPRECATED, WILL BE REMOVED IN THE FUTURE RELEASE
include(":api:dynamic")

include(":validation:api")
include(":validation:spi")
include(":validation:impl")
include(":validation:format")

include(":lang:api")
include(":lang:common")
include(":lang:compiled")
include(":lang:jap")
include(":lang:rt")
include(":lang:ksp")

include(":core:model:api")
include(":core:model:impl")

include(":core:graph:api")
include(":core:graph:impl")

include(":processor:common")
include(":processor:jap")
include(":processor:ksp")

include(":rt:engine")
include(":rt:support")

include(":codegen:impl")
include(":codegen:poetry:api")
include(":codegen:poetry:java")
include(":codegen:poetry:kotlin")

include(":testing:tests")
include(":testing:procedural")
include(":testing:source-set")