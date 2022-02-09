plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":base"))

    api(project(":lang"))
    implementation(kotlin("reflect"))
}