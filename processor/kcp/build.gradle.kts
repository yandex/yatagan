import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.Test

plugins {
    id("yatagan.implementation-artifact")
}

// The published `processor-kcp` artifact is a self-contained fat jar: kotlinc loads it as a single
// compiler plugin classpath entry, so every dependency is embedded (and third-party ones relocated)
// instead of being declared in the POM.
val embedded = configurations.dependencyScope("embedded")
val embeddedClasspath = configurations.resolvable("embeddedClasspath") {
    extendsFrom(embedded.get())
}

configurations.named("compileOnly") { extendsFrom(embedded.get()) }
configurations.named("testImplementation") { extendsFrom(embedded.get()) }

dependencies {
    compileOnly(kotlin("compiler-embeddable"))

    add(embedded.name, project(":processor:common"))
    add(embedded.name, project(":lang:kcp"))
    add(embedded.name, project(":codegen:ir"))
    add(embedded.name, project(":core:model:impl"))
    add(embedded.name, project(":core:graph:impl"))
    add(embedded.name, project(":validation:impl"))
    add(embedded.name, project(":validation:format"))
    add(embedded.name, project(":validation:spi"))

    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(project(":api:public"))
    testImplementation(libs.testing.junit4)
    testImplementation(libs.testing.assertj)
}

tasks.jar { enabled = false }

val shadowJar = tasks.register<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    from(sourceSets.main.map { it.output })
    configurations.add(embeddedClasspath.get())
    dependencies {
        // Provided by the hosting Kotlin compiler.
        exclude(dependency("org.jetbrains:.*"))
        exclude(dependency("org.jetbrains.kotlin:.*"))
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    exclude("module-info.class")
    exclude("META-INF/versions/*/module-info.class")
    relocate("org.logicng", "com.yandex.yatagan.shaded.logicng")
    relocate("com.squareup.javapoet", "com.yandex.yatagan.shaded.javapoet")
}

for (name in arrayOf("apiElements", "runtimeElements")) {
    configurations.named(name) { artifacts.removeIf { true } }
    artifacts.add(name, shadowJar)
}

tasks.named<Test>("test") {
    dependsOn(shadowJar)
    inputs.file(shadowJar.flatMap { it.archiveFile })
    systemProperty(
        "com.yandex.yatagan.kcp.pluginJar",
        shadowJar.get().archiveFile.get().asFile.absolutePath,
    )
}
