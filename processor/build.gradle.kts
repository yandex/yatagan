plugins {
    id("daggerlite.artifact")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":validation-impl"))
    implementation(project(":validation-format"))
    implementation(project(":graph-impl"))
    implementation(project(":core-impl"))
    implementation(project(":generator"))
    implementation(project(":spi-impl"))
    implementation(kotlin("stdlib"))
}