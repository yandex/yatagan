plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":core-impl"))
    implementation(kotlin("stdlib"))
}