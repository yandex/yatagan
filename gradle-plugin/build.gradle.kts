import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("yatagan.gradle-plugin-artifact")
}

kotlin {
    compilerOptions {
        // Gradle embeds its own, older Kotlin runtime.
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

dependencies {
    compileOnly(libs.kotlin.gradlePluginApi)
}

gradlePlugin {
    plugins {
        create("yatagan") {
            id = "com.yandex.yatagan"
            implementationClass = "com.yandex.yatagan.gradle.plugin.YataganGradleSubplugin"
        }
    }
}

tasks.processResources {
    from(rootProject.layout.projectDirectory.file("yatagan.version"))
}
