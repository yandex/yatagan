plugins {
    id("daggerlite.artifact")
    id("java-test-fixtures")
}

val kspVersion: String by extra

dependencies {
    implementation(project(":api"))
    implementation(project(":processor"))
    implementation(project(":lang-ksp"))

    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:$kspVersion")

    testFixturesImplementation(testFixtures(project(":testing")))
    testFixturesImplementation(kotlin("test"))
}