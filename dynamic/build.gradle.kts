plugins {
    id("daggerlite.artifact")
}

val junitVersion: String by extra

dependencies {
    api(project(":api"))

    implementation(project(":base"))
    implementation(project(":graph-impl"))
    implementation(project(":core-impl"))
    implementation(project(":lang-rt"))
    implementation(kotlin("stdlib"))
}