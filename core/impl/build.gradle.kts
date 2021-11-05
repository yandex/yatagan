plugins {
    id("daggerlite.artifact")
}

dependencies {
    api(project(":core"))
    implementation(project(":base"))
    implementation(project(":core-lang"))

    implementation(kotlin("stdlib"))
}