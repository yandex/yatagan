plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":lang:api"))
    api(project(":validation:api"))
}

kotlin {
    explicitApi()
}