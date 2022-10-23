plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":lang:api"))
    implementation(project(":base"))

    implementation(kotlin("stdlib"))
}