import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

plugins {
    id("yatagan.implementation-artifact")
}

dependencies {
    implementation(project(":core:graph:api"))
    compileOnly(kotlin("compiler-embeddable"))

    testImplementation(project(":api:public"))
    testImplementation(project(":core:graph:impl"))
    testImplementation(project(":core:model:impl"))
    testImplementation(project(":lang:kcp"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(libs.testing.junit4)
    testImplementation(libs.testing.assertj)
}

val testPluginJar = tasks.register<Jar>("testPluginJar") {
    archiveClassifier.set("test-plugin")
    from(sourceSets.main.map { it.output })
    from(sourceSets.test.map { it.output })
}

tasks.named<Test>("test") {
    dependsOn(testPluginJar)
    inputs.file(testPluginJar.flatMap { it.archiveFile })
    systemProperty(
        "com.yandex.yatagan.codegen.ir.testPluginJar",
        testPluginJar.get().archiveFile.get().asFile.absolutePath,
    )
}
