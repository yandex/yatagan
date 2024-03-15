plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":lang:api"))
    api(project(":core:model:api"))
}

kotlin {
    explicitApi()
}
