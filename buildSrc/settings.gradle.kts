// This file is also applied in root `settings.gradle.kts` script.
// Only version catalog declaration is allowed here, don't do other things here

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    versionCatalogs {
        create("libs") {
            val kotlinVersion = "1.7.10"
            val kspVersion = "1.0.6"
            version("kotlin", kotlinVersion)
            version("ksp", "$kotlinVersion-$kspVersion")

            library("kotlin-gradle", "org.jetbrains.kotlin", "kotlin-gradle-plugin").versionRef("kotlin")

            library("dokka-gradle", "org.jetbrains.dokka", "dokka-gradle-plugin").versionRef("kotlin")
            library("dokka-base", "org.jetbrains.dokka", "dokka-base").versionRef("kotlin")
            library("dokka-core", "org.jetbrains.dokka", "dokka-core").versionRef("kotlin")

            library("ksp-impl", "com.google.devtools.ksp", "symbol-processing").versionRef("ksp")
            library("ksp-api", "com.google.devtools.ksp", "symbol-processing-api").versionRef("ksp")

            library("autoCommon", "com.google.auto:auto-common:1.2.1")
            library("kotlinx-cli", "org.jetbrains.kotlinx:kotlinx-cli:0.3.4")

            library("poets-java", "com.squareup:javapoet:1.13.0")
            library("poets-kotlin", "com.squareup:kotlinpoet:1.11.0")
        }

        create("testingLibs") {
            library("kotlinCompileTesting", "com.github.tschuchortdev:kotlin-compile-testing-ksp:1.4.9")
            library("junit4", "junit:junit:4.13.2")
            library("mockito-kotlin", "org.mockito.kotlin:mockito-kotlin:4.0.0")
            library("assertj", "org.assertj:assertj-core:3.23.1")
        }
    }
}