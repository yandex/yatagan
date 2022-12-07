// This file is also applied in root `settings.gradle.kts` script.
// Only version catalog declaration is allowed here, don't do other things here

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    versionCatalogs {
        create("libs") {
            val kotlinVersion = "1.7.20"
            val kspVersion = "1.0.8"
            version("kotlin", kotlinVersion)
            version("ksp", "$kotlinVersion-$kspVersion")

            library("kotlin-gradle", "org.jetbrains.kotlin", "kotlin-gradle-plugin").versionRef("kotlin")

            library("ksp-impl", "com.google.devtools.ksp", "symbol-processing").versionRef("ksp")
            library("ksp-api", "com.google.devtools.ksp", "symbol-processing-api").versionRef("ksp")

            library("autoCommon", "com.google.auto:auto-common:1.2.1")

            library("poets-java", "com.squareup:javapoet:1.13.0")
            library("poets-kotlin", "com.squareup:kotlinpoet:1.11.0")
        }

        create("testingLibs") {
            library("roomCompileTesting", "androidx.room:room-compiler-processing-testing:2.5.0-beta02")
            library("junit4", "junit:junit:4.13.2")
            library("mockito-kotlin", "org.mockito.kotlin:mockito-kotlin:4.0.0")
            library("assertj", "org.assertj:assertj-core:3.23.1")
        }
    }
}