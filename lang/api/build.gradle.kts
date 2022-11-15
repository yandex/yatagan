plugins {
    id("yatagan.artifact")
    id("yatagan.documented")
}

configurations.configureEach {
    resolutionStrategy {
        // Force KSP version as testing framework may depend on an older version.
        force(libs.ksp.impl)
        force(libs.ksp.api)
    }
}

dependencies {
    testImplementation(project(":base"))
    testImplementation(project(":lang:jap"))
    testImplementation(project(":lang:ksp"))
    testImplementation(project(":lang:rt"))
    testImplementation(project(":testing:source-set"))

    testImplementation(testingLibs.junit4)
    testImplementation(testingLibs.kotlinCompileTesting)
    testImplementation(testingLibs.assertj)
}

kotlin {
    explicitApi()
}