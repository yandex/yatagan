plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":lang"))
    api(project(":validation"))
    implementation(project(":api"))

    implementation(kotlin("stdlib"))
}