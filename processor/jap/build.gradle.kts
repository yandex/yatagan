plugins {
    id("daggerlite.artifact")
    id("daggerlite.documented")
}

val autoCommonVersion: String by extra
val junitVersion: String by extra

dependencies {
    implementation(project(":api"))
    implementation(project(":processor"))
    implementation(project(":lang-jap"))
    implementation(kotlin("stdlib"))

    api("com.google.auto:auto-common:$autoCommonVersion")
}