plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":instrumentation:api"))
    api(project(":core:graph:api"))
}

kotlin {
    explicitApi()
}
