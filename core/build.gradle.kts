plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":core-lang"))
    implementation(project(":api"))

    implementation(kotlin("stdlib"))
}