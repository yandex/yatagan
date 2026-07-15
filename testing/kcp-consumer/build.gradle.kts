import org.gradle.api.tasks.ClasspathNormalizer
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    id("yatagan.test-only-module")
}

val kcpPlugin = configurations.register("kcpPlugin")

dependencies {
    implementation(project(":api:public"))
    kcpPlugin(project(":processor:kcp"))

    testImplementation(libs.testing.junit4)
    testImplementation(libs.testing.assertj)
}

tasks.withType<KotlinJvmCompile>().configureEach {
    dependsOn(kcpPlugin)
    inputs.files(kcpPlugin).withNormalizer(ClasspathNormalizer::class.java)
    compilerOptions.freeCompilerArgs.addAll(kcpPlugin.map { classpath ->
        listOf("-Xplugin=${classpath.joinToString(",") { it.absolutePath }}")
    })
}
