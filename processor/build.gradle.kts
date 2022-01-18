plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":validation-impl"))
    implementation(project(":graph-impl"))
    implementation(project(":core-impl"))
    implementation(project(":generator"))
    implementation(kotlin("stdlib"))
}