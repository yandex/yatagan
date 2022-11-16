plugins {
    id("yatagan.artifact")
    id("yatagan.documented")
}

dependencies {
    api(project(":core:model:api"))
}

kotlin {
    explicitApi()
}