plugins {
    id("yatagan.implementation-artifact")
}

dependencies {
    api(project(":core:graph:api"))
    api(project(":instrumentation:spi"))

    implementation(project(":base:impl"))
    implementation(project(":validation:format"))
    implementation(project(":instrumentation:impl"))
}