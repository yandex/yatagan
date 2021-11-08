plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":core-impl"))
    implementation(kotlin("stdlib"))
    implementation(project(":generator"))
}