rootProject.name = "dagger-lite"

include("api")
include("api-compiled")
project(":api-compiled").projectDir = file("api/compiled")
include("api-dynamic")
project(":api-dynamic").projectDir = file("api/dynamic")

include("base")

include("validation")
include("validation-impl")

include("lang")

include("lang-common")
project(":lang-common").projectDir = file("lang/common")

include("lang-ksp")
project(":lang-ksp").projectDir = file("lang/ksp")

include("lang-jap")
project(":lang-jap").projectDir = file("lang/jap")

include("lang-rt")
project(":lang-rt").projectDir = file("lang/rt")

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
include("testing-generator")
project(":testing-generator").projectDir = file("testing/generator")

include("testing-dokka")
project(":testing-dokka").projectDir = file("testing/dokka")

include("spi")
include("spi-impl")
project(":spi-impl").projectDir = file("spi/impl")

project(":validation-impl").projectDir = file("validation/impl")

include("validation-format")
project(":validation-format").projectDir = file("validation/format")

project(":core-impl").projectDir = file("core/impl")

project(":graph").projectDir = file("graph")
project(":graph-impl").projectDir = file("graph/impl")

project(":generator-poetry").projectDir = file("generator/poetry")
project(":generator-lang").projectDir = file("generator/lang")