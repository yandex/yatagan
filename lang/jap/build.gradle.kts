plugins {
    id("daggerlite.artifact")
}

val autoCommonVersion: String by extra

dependencies {
    implementation(project(":api"))
    implementation(project(":base"))

    api(project(":lang"))
    api(project(":generator-lang"))

    implementation("com.google.auto:auto-common:$autoCommonVersion")

    implementation(kotlin("stdlib"))
}