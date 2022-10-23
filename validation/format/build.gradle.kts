plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":core:model:api"))
    api(project(":core:graph:api"))
    api(project(":validation:api"))

    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
}