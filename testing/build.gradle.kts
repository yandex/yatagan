plugins {
    id("daggerlite.base-module")
    id("java-test-fixtures")
}

val kotlinCompileTestingVersion: String by extra

dependencies {
    implementation(kotlin("stdlib"))

    testFixturesApi(project(":api"))
    testFixturesApi("com.github.tschuchortdev:kotlin-compile-testing-ksp:$kotlinCompileTestingVersion")

    testImplementation(testFixtures(project(":ksp-driver")))
    testImplementation(testFixtures(project(":jap-driver")))
    testImplementation(kotlin("test"))
    testImplementation(project(":ksp-driver-lang"))
    testImplementation(project(":jap-driver-lang"))
}