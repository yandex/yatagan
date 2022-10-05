plugins {
    id("daggerlite.base-module")
}

val kotlinCompileTestingVersion: String by extra
val kspVersion: String by extra

configurations.configureEach {
    resolutionStrategy {
        // Force KSP version as testing framework may depend on an older version.
        force("com.google.devtools.ksp:symbol-processing:$kspVersion")
        force("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
    }
}

dependencies {
    api("com.github.tschuchortdev:kotlin-compile-testing-ksp:$kotlinCompileTestingVersion")
}