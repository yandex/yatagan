plugins {
    id("yatagan.implementation-artifact")
}

dependencies {
    api(project(":instrumentation:api"))
    api(project(":instrumentation:spi"))
    api(project(":core:graph:api"))

    implementation(project(":core:model:impl"))
}
