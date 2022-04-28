plugins {
    id("daggerlite.artifact")
    id("daggerlite.documented")
}

dependencies {
    api(project(":core"))
    implementation(kotlin("stdlib"))
}