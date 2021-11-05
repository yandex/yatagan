plugins {
    id("daggerlite.artifact")
}

val autoCommonVersion: String by extra

dependencies {
    implementation(project(":api"))
    implementation(project(":base"))
    implementation(project(":core-lang"))
    implementation(project(":generator-lang"))

    implementation("com.google.auto:auto-common:$autoCommonVersion")

    implementation(kotlin("stdlib"))
}