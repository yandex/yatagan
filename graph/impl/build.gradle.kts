plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":base"))
    api(project(":graph"))
    implementation(kotlin("stdlib"))
}