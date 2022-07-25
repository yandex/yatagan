plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":validation-format"))
    api(project(":graph"))
    implementation(kotlin("stdlib"))
}