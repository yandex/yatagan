plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":core"))
    implementation(project(":validation-format"))
    implementation(project(":api"))
    implementation(project(":base"))

    implementation(kotlin("stdlib"))
}