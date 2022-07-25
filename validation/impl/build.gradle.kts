plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":validation"))
    implementation(project(":base"))

    implementation(kotlin("stdlib"))
}