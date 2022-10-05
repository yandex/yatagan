plugins {
    id("daggerlite.artifact")
    id("daggerlite.documented")
}

val kotlinCompileTestingVersion: String by extra
val kspVersion: String by extra
val junitVersion: String by extra

configurations.configureEach {
    resolutionStrategy {
        // Force KSP version as testing framework may depend on an older version.
        force("com.google.devtools.ksp:symbol-processing:$kspVersion")
        force("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation(project(":base"))
    testImplementation(project(":lang-jap"))
    testImplementation(project(":lang-ksp"))
    testImplementation(project(":lang-rt"))
    testImplementation(project(":testing-source-set"))
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:$kotlinCompileTestingVersion")
    testImplementation("junit:junit:$junitVersion")
    testImplementation("org.assertj:assertj-core:3.23.1")
}