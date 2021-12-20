plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":validation"))

    implementation(kotlin("stdlib"))
}