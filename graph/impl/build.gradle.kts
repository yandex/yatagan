plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":validation-impl"))
    api(project(":graph"))
    implementation(kotlin("stdlib"))
}