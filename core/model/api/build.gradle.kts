plugins {
    id("yatagan.artifact")
    id("yatagan.documented")
}

dependencies {
    api(project(":lang:api"))
    api(project(":validation:api"))

    implementation(kotlin("stdlib"))
}

kotlin {
    explicitApi()
}