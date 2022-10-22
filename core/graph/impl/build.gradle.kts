plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":validation:format"))
    api(project(":core:graph:api"))
    implementation(kotlin("stdlib"))
}