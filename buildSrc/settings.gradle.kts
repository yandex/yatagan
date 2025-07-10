// This file is also applied in root `settings.gradle.kts` script.
// Only version catalog declaration is allowed here, don't do other things here

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val kotlinVersion = "1.9.24"
            val kspVersion = "1.0.20"
            version("kotlin", kotlinVersion)
            version("ksp", "$kotlinVersion-$kspVersion")

            library("kotlin-gradle", "org.jetbrains.kotlin", "kotlin-gradle-plugin").versionRef("kotlin")
            library("dokka-gradle", "org.jetbrains.dokka", "dokka-gradle-plugin").version("1.9.20")
            library("publish-gradle", "com.vanniktech:gradle-maven-publish-plugin:0.30.0")

            library("ksp-api", "com.google.devtools.ksp", "symbol-processing-api").versionRef("ksp")
            library("ksp-gradle", "com.google.devtools.ksp", "com.google.devtools.ksp.gradle.plugin").versionRef("ksp")

            library("autoCommon", "com.google.auto:auto-common:1.2.1")

            library("poets-java", "com.squareup:javapoet:1.13.0")
            library("poets-kotlin", "com.squareup:kotlinpoet:1.11.0")

            library("logicng", "org.logicng:logicng:2.5.0")

            val yataganDogFood = "1.2.1"
            library("yataganDogFood-api", "com.yandex.yatagan", "api-compiled").version(yataganDogFood)
            library("yataganDogFood-ksp", "com.yandex.yatagan", "processor-ksp").version(yataganDogFood)
        }

        create("testingLibs") {
            library("roomCompileTesting", "androidx.room:room-compiler-processing-testing:2.7.0")
            library("junit4", "junit:junit:4.13.2")
            library("mockito-kotlin", "org.mockito.kotlin:mockito-kotlin:4.0.0")
            library("assertj", "org.assertj:assertj-core:3.23.1")
        }
    }
}
