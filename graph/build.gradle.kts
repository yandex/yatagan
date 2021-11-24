plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":core"))
    implementation(kotlin("stdlib"))
}