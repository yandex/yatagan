plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":lang"))

    implementation(kotlin("stdlib"))
}