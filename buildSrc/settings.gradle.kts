// This file is also applied in root `settings.gradle.kts` script.
// Only version catalog declaration is allowed here, don't do other things here

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val kotlinVersion = "1.8.10"
            val kspVersion = "1.0.9"
            version("kotlin", kotlinVersion)
            version("ksp", "$kotlinVersion-$kspVersion")

            library("kotlin-gradle", "org.jetbrains.kotlin", "kotlin-gradle-plugin").versionRef("kotlin")
            library("kotlin-binaryCompatibilityGradle", "org.jetbrains.kotlinx.binary-compatibility-validator",
                "org.jetbrains.kotlinx.binary-compatibility-validator.gradle.plugin").version("0.13.2")
            library("dokka-gradle", "org.jetbrains.dokka", "dokka-gradle-plugin").versionRef("kotlin")
            library("nexusPublish-gradle", "io.github.gradle-nexus:publish-plugin:1.3.0")

            library("ksp-api", "com.google.devtools.ksp", "symbol-processing-api").versionRef("ksp")

            library("autoCommon", "com.google.auto:auto-common:1.2.1")

            library("poets-java", "com.squareup:javapoet:1.13.0")
            library("poets-kotlin", "com.squareup:kotlinpoet:1.11.0")
        }

        create("testingLibs") {
            library("roomCompileTesting", "androidx.room:room-compiler-processing-testing:2.6.0-alpha01")
            library("junit4", "junit:junit:4.13.2")
            library("mockito-kotlin", "org.mockito.kotlin:mockito-kotlin:4.0.0")
            library("assertj", "org.assertj:assertj-core:3.23.1")
        }
    }
}