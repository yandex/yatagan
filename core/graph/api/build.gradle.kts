plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":core:model:api"))
}

kotlin {
    explicitApi()
}