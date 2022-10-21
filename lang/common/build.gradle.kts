plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":lang"))
    implementation(project(":base"))

    implementation(kotlin("stdlib"))
}