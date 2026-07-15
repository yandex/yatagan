import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test

plugins {
    id("yatagan.implementation-artifact")
}

dependencies {
    api(project(":lang:compiled"))

    compileOnly(kotlin("compiler-embeddable"))
    implementation(project(":base:impl"))

    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(project(":api:public"))
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
        "com.yandex.yatagan.lang.kcp.testPluginJar",
        testPluginJar.get().archiveFile.get().asFile.absolutePath,
    )
}
