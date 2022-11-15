rootProject.name = "yatagan"

include(":base")

include(":api:public")
include(":api:common")
include(":api:compiled")
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
include(":codegen:poetry")

include(":testing:tests")
include(":testing:procedural")
include(":testing:doc-testing")
include(":testing:source-set")