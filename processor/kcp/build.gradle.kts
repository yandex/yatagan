import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

plugins {
    id("yatagan.base-module")
}

dependencies {
    compileOnly(kotlin("compiler-embeddable"))
    implementation(project(":processor:common"))
    implementation(project(":lang:kcp"))
    implementation(project(":codegen:ir"))
    implementation(project(":core:model:impl"))
    implementation(project(":core:graph:impl"))
    implementation(project(":validation:impl"))
    implementation(project(":validation:format"))

    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(project(":api:public"))
    testImplementation(libs.testing.junit4)
    testImplementation(libs.testing.assertj)
}

val pluginJar = tasks.named<Jar>("jar")

tasks.named<Test>("test") {
    dependsOn(pluginJar)
    inputs.file(pluginJar.flatMap { it.archiveFile })
    systemProperty(
        "com.yandex.yatagan.kcp.pluginJar",
        pluginJar.get().archiveFile.get().asFile.absolutePath,
    )
}
