plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":base"))
    implementation(project(":lang"))

    implementation(kotlin("stdlib"))
}