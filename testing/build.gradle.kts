plugins {
    id("daggerlite.base-module")
    id("java-test-fixtures")
}

val kotlinCompileTestingVersion: String by extra

dependencies {
    testFixturesApi(project(":api-compiled"))
    testFixturesApi("com.github.tschuchortdev:kotlin-compile-testing-ksp:$kotlinCompileTestingVersion")
    testFixturesImplementation(project(":processor"))
    testFixturesImplementation(project(":base"))
    testFixturesImplementation(project(":validation-impl"))
    testFixturesImplementation(kotlin("test"))

    testImplementation(testFixtures(project(":processor-ksp")))
    testImplementation(testFixtures(project(":processor-jap")))
    testImplementation(kotlin("test"))
    testImplementation(project(":validation-impl"))
    testImplementation(project(":lang-ksp"))
    testImplementation(project(":lang-jap"))
    testRuntimeOnly(project(":api-compiled"))
}