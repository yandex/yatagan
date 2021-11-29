plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":lang"))
    implementation(project(":api"))

    implementation(kotlin("stdlib"))
}