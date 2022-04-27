plugins {
    id("daggerlite.artifact")
    id("daggerlite.documented")
}

dependencies {
    api(project(":lang"))
    api(project(":validation"))
    implementation(project(":api"))

    implementation(kotlin("stdlib"))
}