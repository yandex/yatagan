plugins {
    id("daggerlite.artifact")
    id("daggerlite.documented")
}

dependencies {
    api(project(":lang:api"))
    api(project(":validation:api"))

    implementation(kotlin("stdlib"))
}