plugins {
    kotlin("jvm")
    id("maven-publish")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":core-lang"))
    implementation(project(":generator-lang"))

    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:${Versions.Ksp}")
}