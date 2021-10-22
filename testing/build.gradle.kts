plugins {
    kotlin("jvm")
    id("java-test-fixtures")
}

dependencies {
    implementation(kotlin("stdlib"))

    testFixturesApi(project(":api"))
    testFixturesApi("com.github.tschuchortdev:kotlin-compile-testing-ksp:${Versions.KotlinCompileTesting}")

    testImplementation(testFixtures(project(":ksp-driver")))
    testImplementation(kotlin("test"))
}