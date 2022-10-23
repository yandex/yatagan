plugins {
    id("daggerlite.artifact")
    id("daggerlite.documented")
}

dependencies {
    api(project(":core:model:api"))
    implementation(kotlin("stdlib"))
}