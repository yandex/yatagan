plugins {
    id("yatagan.base-module")
}

configurations.configureEach {
    resolutionStrategy {
        // Force KSP version as testing framework may depend on an older version.
        force(libs.ksp.impl)
        force(libs.ksp.api)
    }
}

dependencies {
    api(testingLibs.kotlinCompileTesting)
}