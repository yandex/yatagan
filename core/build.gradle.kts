plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":api"))
    api(project(":core-lang"))

    implementation(kotlin("stdlib"))
}