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
            library("dokka-gradle", "org.jetbrains.dokka", "dokka-gradle-plugin").versionRef("kotlin")
            library("nexusPublish-gradle", "io.github.gradle-nexus:publish-plugin:1.3.0")

            library("ksp-api", "com.google.devtools.ksp", "symbol-processing-api").versionRef("ksp")
            library("ksp-gradle", "com.google.devtools.ksp", "com.google.devtools.ksp.gradle.plugin").versionRef("ksp")

            library("autoCommon", "com.google.auto:auto-common:1.2.1")

            library("poets-java", "com.squareup:javapoet:1.13.0")
            library("poets-kotlin", "com.squareup:kotlinpoet:1.11.0")

            library("logicng", "org.logicng:logicng-j11:2.4.2")

            val yataganDogFood = "1.2.1"
            library("yataganDogFood-api", "com.yandex.yatagan", "api-compiled").version(yataganDogFood)
            library("yataganDogFood-ksp", "com.yandex.yatagan", "processor-ksp").version(yataganDogFood)
        }

        create("testingLibs") {
            library("roomCompileTesting", "androidx.room:room-compiler-processing-testing:2.6.0-beta01")
            library("junit4", "junit:junit:4.13.2")
            library("mockito-kotlin", "org.mockito.kotlin:mockito-kotlin:4.0.0")
            library("assertj", "org.assertj:assertj-core:3.23.1")
        }
    }
}